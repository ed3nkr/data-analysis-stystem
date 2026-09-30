"""네이버 플레이스 공개 방문자 리뷰 수집.

수집 예절:
  - 요청(페이지 이동·더보기 클릭) 사이 최소 2초 간격 (Throttle)
  - since 보다 오래된 리뷰가 나오면 중단(REACHED_SINCE), maxRequests 도달 시 중단(MAX_REQUESTS)
  - 로그인·캡차·접근 제한이 감지되면 재시도 없이 즉시 중단 (AccessBlocked → 503)
  - 로그인이 필요한 정보는 읽지 않는다 (공개 페이지만, 쿠키·계정 없음)
페이지 구조 해석은 전부 parser.py 에 있다.
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import time
from dataclasses import dataclass, field
from datetime import date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx

from . import parser
from .config import Settings
from .errors import AccessBlocked, InvalidPlaceUrl, PageStructureChanged, PlaceNotFound
from .hashing import author_hash, dedup_key

log = logging.getLogger("collector")
KST = ZoneInfo("Asia/Seoul")


def today_kst() -> date:
    return datetime.now(KST).date()


class MaxRequestsReached(Exception):
    pass


class Throttle:
    """요청 사이 최소 간격을 지키고, 요청 수를 센다."""

    def __init__(self, min_interval: float, max_requests: int, clock=time.monotonic, sleep=asyncio.sleep):
        self.min_interval = min_interval
        self.max_requests = max_requests
        self.count = 0
        self._last: float | None = None
        self._clock = clock
        self._sleep = sleep

    async def acquire(self) -> None:
        if self.count >= self.max_requests:
            raise MaxRequestsReached()
        if self._last is not None:
            wait = self.min_interval - (self._clock() - self._last)
            if wait > 0:
                await self._sleep(wait)
        self._last = self._clock()
        self.count += 1


# ---------------------------------------------------------------------------
# 페이지 세션: 실제(Playwright) / fixture
# ---------------------------------------------------------------------------

@dataclass
class Snapshot:
    url: str
    status: int | None
    html: str
    payloads: list = field(default_factory=list)   # 이번 단계에서 받은 GraphQL JSON 응답들


class PlaywrightSession:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._pw = None
        self._browser = None
        self.page = None
        self._responses: list = []

    async def __aenter__(self):
        from playwright.async_api import async_playwright
        self._pw = await async_playwright().start()
        launch_args = {"headless": self.settings.headless}
        proxy = os.getenv("HTTPS_PROXY") or os.getenv("https_proxy")
        if proxy:  # 사내망 등 프록시 환경 (httpx 는 환경변수를 자동으로 따른다)
            launch_args["proxy"] = {"server": proxy}
        self._browser = await self._pw.chromium.launch(**launch_args)
        context = await self._browser.new_context(
            user_agent=self.settings.user_agent, locale="ko-KR", timezone_id="Asia/Seoul",
            viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
        self.page = await context.new_page()
        self.page.set_default_timeout(self.settings.page_timeout_ms)
        self.page.on("response", self._on_response)
        return self

    async def __aexit__(self, *exc):
        if self._browser:
            await self._browser.close()
        if self._pw:
            await self._pw.stop()

    def _on_response(self, response):
        if "graphql" in response.url and response.request.method == "POST":
            self._responses.append(response)

    async def _drain_payloads(self) -> list:
        payloads = []
        responses, self._responses = self._responses, []
        for r in responses:
            try:
                payloads.append(await r.json())
            except Exception:  # 본문이 JSON 이 아니거나 이미 해제됨
                continue
        return payloads

    async def _settle(self):
        try:
            await self.page.wait_for_load_state("networkidle", timeout=8000)
        except Exception:
            pass

    async def open(self, url: str) -> Snapshot:
        resp = await self.page.goto(url, wait_until="domcontentloaded")
        await self._settle()
        return Snapshot(self.page.url, resp.status if resp else None, await self.page.content(),
                        await self._drain_payloads())

    async def has_more(self) -> bool:
        return await self._more_locator() is not None

    async def click_more(self) -> Snapshot:
        loc = await self._more_locator()
        await loc.scroll_into_view_if_needed()
        await loc.click()
        await self._settle()
        return Snapshot(self.page.url, None, await self.page.content(), await self._drain_payloads())

    async def _more_locator(self):
        for sel in parser.SELECTORS["more_button"]:
            loc = self.page.locator(sel).last
            try:
                if await loc.count() > 0 and await loc.is_visible():
                    return loc
            except Exception:
                continue
        return None


class FixtureSession:
    """COLLECTOR_FIXTURE_DIR 의 저장된 페이지/응답을 읽는다 (오프라인 리허설·테스트용, 네트워크 사용 안 함).

    {dir}/reviews_{placeId}.html          : 첫 화면 HTML
    {dir}/reviews_{placeId}_more{n}.json  : n 번째 더보기 클릭 시 GraphQL 응답 (n=1,2,...)
    """

    def __init__(self, fixture_dir: str, place_id: str):
        self.dir = Path(fixture_dir)
        self.place_id = place_id
        self.clicks = 0

    async def __aenter__(self):
        return self

    async def __aexit__(self, *exc):
        return None

    async def open(self, url: str) -> Snapshot:
        path = self.dir / f"reviews_{self.place_id}.html"
        if not path.exists():
            return Snapshot(url, 404, "<html><body>페이지를 찾을 수 없습니다</body></html>")
        return Snapshot(url, 200, path.read_text(encoding="utf-8"))

    async def has_more(self) -> bool:
        return (self.dir / f"reviews_{self.place_id}_more{self.clicks + 1}.json").exists()

    async def click_more(self) -> Snapshot:
        self.clicks += 1
        path = self.dir / f"reviews_{self.place_id}_more{self.clicks}.json"
        html = (self.dir / f"reviews_{self.place_id}.html").read_text(encoding="utf-8")
        return Snapshot("fixture://more", 200, html, [json.loads(path.read_text(encoding="utf-8"))])


# ---------------------------------------------------------------------------
# 리뷰 수집
# ---------------------------------------------------------------------------

@dataclass
class CollectResult:
    status: str
    stopped_reason: str
    request_count: int
    reviews: list[dict]


def _key(r: parser.RawReview) -> str:
    return r.review_id or f"{r.written_at}|{r.nickname}|{r.content[:50]}"


def _check_block(snap: Snapshot) -> None:
    reason = parser.detect_block(snap.url, snap.status, snap.html)
    if reason:
        log.warning("접근 제한 감지 → 즉시 중단: %s", reason)
        raise AccessBlocked(f"네이버 접근이 제한되어 수집을 중단했습니다 ({reason}).")


async def collect_reviews(settings: Settings, place_id: str, since: date, max_requests: int,
                          session=None, throttle: Throttle | None = None) -> CollectResult:
    today = today_kst()
    throttle = throttle or Throttle(settings.min_interval_sec, max_requests)
    if session is None:
        session = (FixtureSession(settings.fixture_dir, place_id) if settings.fixture_dir
                   else PlaywrightSession(settings))
    merged: dict[str, parser.RawReview] = {}

    def absorb(snap: Snapshot, use_initial_sources: bool) -> int:
        before = len(merged)
        found: list[parser.RawReview] = []
        for payload in snap.payloads:
            found += parser.parse_graphql_reviews(payload, today) or []
        if use_initial_sources:
            found += parser.parse_apollo_reviews(parser.extract_apollo_state(snap.html), today)
        if not found:
            found = parser.parse_dom_reviews(snap.html, today)
        for r in found:
            merged.setdefault(_key(r), r)
        return len(merged) - before

    async with session:
        await throttle.acquire()
        snap = await session.open(parser.REVIEW_URL.format(place_id=place_id))
        _check_block(snap)
        if snap.status == 404:
            raise PlaceNotFound("플레이스를 찾을 수 없습니다.")
        absorb(snap, use_initial_sources=True)
        if not merged and not parser.has_no_review_marker(snap.html) and not parser.has_review_container(snap.html):
            raise PageStructureChanged("리뷰 목록 영역을 찾지 못했습니다. parser.py 의 셀렉터를 확인하세요.")

        while True:
            if merged and min(r.written_at for r in merged.values()) < since:
                stopped = "REACHED_SINCE"
                break
            if not await session.has_more():
                stopped = "END_OF_LIST"
                break
            try:
                await throttle.acquire()
            except MaxRequestsReached:
                stopped = "MAX_REQUESTS"
                break
            snap = await session.click_more()
            _check_block(snap)
            if absorb(snap, use_initial_sources=False) == 0:
                stopped = "END_OF_LIST"   # 더보기를 눌러도 새 리뷰가 없음
                break

    reviews = []
    for r in sorted(merged.values(), key=lambda x: x.written_at, reverse=True):
        if r.written_at < since:
            continue
        a_hash = author_hash(r.nickname, settings.author_hash_salt)   # 닉네임 원문은 여기서 버린다
        reviews.append({
            "writtenAt": r.written_at.isoformat(),
            "visitedAt": r.visited_at.isoformat() if r.visited_at else None,
            "rating": r.rating,
            "content": r.content,
            "authorHash": a_hash,
            "dedupKey": dedup_key(r.written_at, a_hash, r.content),
        })
    log.info("place=%s 수집 완료: %d건, 요청 %d회, 중단 사유 %s", place_id, len(reviews), throttle.count, stopped)
    return CollectResult("COMPLETED", stopped, throttle.count, reviews)


# ---------------------------------------------------------------------------
# 플레이스 링크 → placeId, 이름, 주소
# ---------------------------------------------------------------------------

async def resolve_place(settings: Settings, place_url: str) -> parser.PlaceInfo:
    url = (place_url or "").strip()
    if not url:
        raise InvalidPlaceUrl("플레이스 링크가 비어 있습니다.")
    throttle = Throttle(settings.min_interval_sec, max_requests=4)

    if settings.fixture_dir:
        place_id = parser.extract_place_id(url)
        if not place_id:
            raise InvalidPlaceUrl("지원하지 않는 플레이스 링크입니다.")
        path = Path(settings.fixture_dir) / f"place_{place_id}.html"
        info = parser.parse_place_home(path.read_text(encoding="utf-8"), place_id) if path.exists() else None
        if not info:
            raise PlaceNotFound("플레이스를 찾을 수 없습니다.")
        return info

    headers = {"User-Agent": settings.user_agent, "Accept-Language": "ko-KR,ko;q=0.9"}
    async with httpx.AsyncClient(headers=headers, follow_redirects=True, timeout=15) as client:
        if parser.is_short_link(url):
            await throttle.acquire()
            resp = await client.get(url if "://" in url else "https://" + url)
            _check_block(Snapshot(str(resp.url), resp.status_code, resp.text))
            url = str(resp.url)
            place_id = parser.extract_place_id(url)
        else:
            place_id = parser.extract_place_id(url)
        if not place_id:
            raise InvalidPlaceUrl("지원하지 않는 플레이스 링크입니다. (m.place.naver.com, map.naver.com, naver.me)")

        await throttle.acquire()
        home = parser.HOME_URL.format(place_id=place_id)
        resp = await client.get(home)
        _check_block(Snapshot(str(resp.url), resp.status_code, resp.text))
        if resp.status_code == 404:
            raise PlaceNotFound("플레이스를 찾을 수 없습니다.")
        info = parser.parse_place_home(resp.text, place_id)

    if info is None:
        # 서버 렌더링 HTML 에 정보가 없으면 브라우저로 한 번 더 연다
        await throttle.acquire()
        async with PlaywrightSession(settings) as s:
            snap = await s.open(home)
        _check_block(snap)
        info = parser.parse_place_home(snap.html, place_id)
    if info is None:
        raise PlaceNotFound("플레이스 정보를 찾을 수 없습니다.")
    return info
