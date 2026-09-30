"""네이버 플레이스 페이지 파싱 — 페이지 구조에 의존하는 코드는 전부 이 파일에 모은다.

구조가 바뀌면 이 파일만 고치면 된다. 고칠 때는 tools/dump_page.py 로 실제 페이지를 저장해서
tests/fixtures 에 넣고 tests/test_parser.py 를 먼저 맞춘다.

리뷰는 세 경로로 읽는다 (앞의 것이 안정적):
  1) 더보기 클릭 시 페이지가 호출하는 GraphQL 응답(JSON) 의 visitorReviews.items
  2) 첫 화면 HTML 에 들어 있는 window.__APOLLO_STATE__ 의 VisitorReview:* 객체
  3) DOM (li 목록) — 위 두 가지가 모두 실패할 때의 대비책
"""
from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import date
from urllib.parse import urlparse

from bs4 import BeautifulSoup

# ---------------------------------------------------------------------------
# URL
# ---------------------------------------------------------------------------

REVIEW_URL = "https://m.place.naver.com/restaurant/{place_id}/review/visitor?reviewSort=recent"
HOME_URL = "https://m.place.naver.com/restaurant/{place_id}/home"

SHORT_LINK_HOSTS = {"naver.me"}
_PLACE_ID_PATTERNS = [
    # m.place.naver.com/restaurant/{id}/..., pcmap.place.naver.com/restaurant/{id}, .../place/{id}
    (re.compile(r"^(m\.|pcmap\.)?place\.naver\.com$"), re.compile(r"^/[a-z]+/(\d{3,20})(?:/|$)")),
    # map.naver.com/p/entry/place/{id}, map.naver.com/p/search/xxx/place/{id}, map.naver.com/v5/entry/place/{id}
    (re.compile(r"^(m\.)?map\.naver\.com$"), re.compile(r"/place/(\d{3,20})(?:/|$)")),
]


def is_short_link(url: str) -> bool:
    return (urlparse(_with_scheme(url)).hostname or "").lower() in SHORT_LINK_HOSTS


def extract_place_id(url: str) -> str | None:
    """지원 형식의 링크에서 placeId 를 꺼낸다. 형식이 아니면 None."""
    parsed = urlparse(_with_scheme(url.strip()))
    host = (parsed.hostname or "").lower()
    for host_re, path_re in _PLACE_ID_PATTERNS:
        if host_re.match(host):
            m = path_re.search(parsed.path)
            if m:
                return m.group(1)
    return None


def _with_scheme(url: str) -> str:
    return url if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*://", url) else "https://" + url


# ---------------------------------------------------------------------------
# 접근 제한 감지
# ---------------------------------------------------------------------------

_BLOCK_MARKERS = [
    "자동입력 방지", "자동 입력 방지", "보안문자", "captcha", "비정상적인 접근", "비정상적인 요청",
    "일시적으로 제한", "접근이 제한", "too many requests", "로그인이 필요",
]
_LOGIN_HOSTS = ("nid.naver.com",)


def detect_block(url: str, status: int | None, html: str | None) -> str | None:
    """로그인·캡차·접근 제한으로 보이면 사유 문자열, 아니면 None."""
    host = (urlparse(url).hostname or "").lower()
    if any(host.endswith(h) for h in _LOGIN_HOSTS):
        return f"로그인 페이지로 이동됨 ({host})"
    if status in (401, 403, 429):
        return f"HTTP {status}"
    if html:
        # 본문 텍스트만 검사 (스크립트 안 문자열 오탐 방지)
        text = _visible_text(html).lower()
        for marker in _BLOCK_MARKERS:
            if marker.lower() in text:
                return f"차단/인증 문구 감지: '{marker}'"
    return None


def _visible_text(html: str) -> str:
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    return soup.get_text(" ", strip=True)


# ---------------------------------------------------------------------------
# 날짜
# ---------------------------------------------------------------------------

_DATE_FULL = re.compile(r"(\d{4})[.\-/]\s*(\d{1,2})[.\-/]\s*(\d{1,2})")
_DATE_YY = re.compile(r"^(\d{2})\.(\d{1,2})\.(\d{1,2})\.?")
_DATE_MD = re.compile(r"^(\d{1,2})\.(\d{1,2})\.?")


def parse_naver_date(text: str | None, today: date) -> date | None:
    """'2026.8.15', '2026-08-15T..', '26.8.15.토', '8.15.금' 형식을 날짜로. 연도가 없으면 올해(미래면 작년)."""
    if not text:
        return None
    t = str(text).strip()
    try:
        if m := _DATE_FULL.search(t):
            return date(int(m.group(1)), int(m.group(2)), int(m.group(3)))
        if m := _DATE_YY.match(t):
            return date(2000 + int(m.group(1)), int(m.group(2)), int(m.group(3)))
        if m := _DATE_MD.match(t):
            d = date(today.year, int(m.group(1)), int(m.group(2)))
            return d if d <= today else date(today.year - 1, d.month, d.day)
    except ValueError:
        return None
    return None


# ---------------------------------------------------------------------------
# 리뷰
# ---------------------------------------------------------------------------

@dataclass
class RawReview:
    """페이지에서 읽은 그대로의 리뷰. 닉네임은 해시 후 버린다 (반환하지 않음)."""
    review_id: str | None
    written_at: date
    visited_at: date | None
    rating: float | None
    content: str
    nickname: str | None


def _pick(d: dict, *keys):
    for k in keys:
        v = d.get(k)
        if v not in (None, ""):
            return v
    return None


def _item_to_review(item: dict, today: date, resolve=lambda x: x) -> RawReview | None:
    body = _pick(item, "body", "content", "reviewBody", "text")
    written = parse_naver_date(_pick(item, "created", "createdAt", "createdString", "writtenAt", "date"), today)
    if not body or not written:
        return None
    author = resolve(item.get("author")) or {}
    nickname = _pick(author, "nickname", "name") if isinstance(author, dict) else None
    rating = item.get("rating")
    try:
        rating = float(rating) if rating not in (None, "", 0) else None
    except (TypeError, ValueError):
        rating = None
    return RawReview(
        review_id=str(item["id"]) if item.get("id") is not None else None,
        written_at=written,
        visited_at=parse_naver_date(_pick(item, "visited", "visitedAt", "visitDate"), today),
        rating=rating,
        content=str(body).strip(),
        nickname=nickname or _pick(item, "nickname", "authorName"),
    )


def parse_graphql_reviews(payload, today: date) -> list[RawReview] | None:
    """GraphQL 응답(객체 또는 배치 배열)에서 visitorReviews.items 를 읽는다.
    visitorReviews 가 없으면 None (다른 쿼리의 응답)."""
    batches = payload if isinstance(payload, list) else [payload]
    found = False
    reviews: list[RawReview] = []
    for batch in batches:
        data = (batch or {}).get("data") or {}
        vr = data.get("visitorReviews")
        if vr is None:
            continue
        found = True
        for item in vr.get("items") or []:
            r = _item_to_review(item, today)
            if r:
                reviews.append(r)
    return reviews if found else None


_APOLLO_RE = re.compile(r"window\.__APOLLO_STATE__\s*=\s*(\{.*?\});\s*(?:window\.|</script>)", re.S)


def extract_apollo_state(html: str) -> dict | None:
    m = _APOLLO_RE.search(html)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except json.JSONDecodeError:
        return None


def parse_apollo_reviews(state: dict | None, today: date) -> list[RawReview]:
    if not state:
        return []

    def resolve(v):
        if isinstance(v, dict) and "__ref" in v:
            return state.get(v["__ref"])
        return v

    reviews = []
    for key, obj in state.items():
        if key.startswith("VisitorReview:") and isinstance(obj, dict):
            r = _item_to_review(obj, today, resolve)
            if r:
                reviews.append(r)
    return reviews


# DOM 대비책용 셀렉터 (클래스명이 난독화되어 자주 바뀐다 → 바뀌면 여기만 수정)
SELECTORS = {
    "review_items": ["#_review_list > li", "ul#_review_list li", "li.place_apply_pui", "li.pui__X35jYm"],
    "content": [".pui__vn15t2 a", ".pui__vn15t2", ".zPfVt", "[class*='review_text']"],
    "nickname": [".pui__NMi-Dp", ".place_bluelink.pui__NMi-Dp", ".sBWyy", "[class*='nickname']"],
    "dates": ["time"],
    # '더보기' 버튼 (리뷰 목록 아래). 텍스트 기준으로도 찾는다 (naver.py)
    "more_button": ["a.fvwqf", "a:has-text('더보기')", "button:has-text('더보기')"],
    # 리뷰 목록 영역이 있다는 표시 (없으면 구조 변경으로 판단)
    "review_container": ["#_review_list", "ul#_review_list", "[class*='place_section'] ul",
                         "div.place_section_content"],
    "no_review_markers": ["아직 리뷰가 없", "등록된 리뷰가 없", "방문자 리뷰가 없"],
}


def parse_dom_reviews(html: str, today: date) -> list[RawReview]:
    soup = BeautifulSoup(html, "html.parser")
    items = []
    for sel in SELECTORS["review_items"]:
        items = soup.select(sel)
        if items:
            break
    reviews = []
    for li in items:
        content = _first_text(li, SELECTORS["content"])
        nickname = _first_text(li, SELECTORS["nickname"])
        times = [t.get("datetime") or t.get_text(strip=True) for t in li.select("time")]
        dates = [parse_naver_date(t, today) for t in times]
        dates = [d for d in dates if d]
        if not content or not dates:
            continue
        # 모바일 방문자 리뷰: 첫 번째 time = 방문일, 마지막 = 작성일(없으면 방문일)
        visited = dates[0]
        written = dates[-1] if len(dates) > 1 else dates[0]
        reviews.append(RawReview(None, written, visited, None, content, nickname))
    return reviews


def _first_text(node, selectors: list[str]) -> str | None:
    for sel in selectors:
        el = node.select_one(sel)
        if el and el.get_text(strip=True):
            return el.get_text(" ", strip=True)
    return None


def has_review_container(html: str) -> bool:
    soup = BeautifulSoup(html, "html.parser")
    return any(soup.select_one(sel) for sel in SELECTORS["review_container"]) or \
        any(soup.select(sel) for sel in SELECTORS["review_items"])


def has_no_review_marker(html: str) -> bool:
    text = _visible_text(html)
    return any(m in text for m in SELECTORS["no_review_markers"])


# ---------------------------------------------------------------------------
# 플레이스 기본 정보 (이름·주소)
# ---------------------------------------------------------------------------

@dataclass
class PlaceInfo:
    place_id: str
    place_name: str
    address: str | None


def parse_place_home(html: str, place_id: str) -> PlaceInfo | None:
    """플레이스 홈 HTML 에서 이름·주소를 읽는다. 못 찾으면 None."""
    state = extract_apollo_state(html)
    if state:
        for key in (f"PlaceDetailBase:{place_id}", f"RestaurantBase:{place_id}", f"PlaceBase:{place_id}"):
            obj = state.get(key)
            if isinstance(obj, dict) and obj.get("name"):
                return PlaceInfo(place_id, str(obj["name"]).strip(),
                                 _pick(obj, "roadAddress", "address", "fullAddress"))
    soup = BeautifulSoup(html, "html.parser")
    og = soup.select_one("meta[property='og:title']")
    name = None
    if og and og.get("content"):
        name = re.sub(r"\s*[:\-|]\s*네이버.*$", "", og["content"]).strip()
    if not name:
        el = soup.select_one("#_title span, #_title, .GHAhO, .Fc1rA")
        name = el.get_text(strip=True) if el else None
    if not name or name in ("네이버", "네이버 지도", "NAVER"):
        return None
    addr_el = soup.select_one(".LDgIH, .PkgBl, [class*='address']")
    address = addr_el.get_text(" ", strip=True) if addr_el else None
    if not address:
        desc = soup.select_one("meta[property='og:description']")
        address = desc["content"].strip() if desc and desc.get("content") else None
    return PlaceInfo(place_id, name, address)
