"""실제 네이버 플레이스 페이지를 저장해 parser.py 셀렉터를 확인/수정할 때 쓰는 도구.

    python tools/dump_page.py <placeId> [--more 2]

결과: dumps/<placeId>/ 에 home.html, reviews.html, graphql_*.json, screenshot.png 저장.
그 다음 python tools/dump_page.py <placeId> --check 로 저장본에 parser 를 돌려 결과를 확인한다.
요청 간격 2초·더보기 최대 --more 회만 누른다 (수집 예절 동일 적용).
"""
import argparse
import asyncio
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app import parser  # noqa: E402
from app.config import load_settings  # noqa: E402
from app.naver import PlaywrightSession, Throttle, today_kst  # noqa: E402


async def dump(place_id: str, more: int, out: Path):
    settings = load_settings()
    throttle = Throttle(2.0, max_requests=2 + more)
    out.mkdir(parents=True, exist_ok=True)
    async with PlaywrightSession(settings) as s:
        await throttle.acquire()
        snap = await s.open(parser.HOME_URL.format(place_id=place_id))
        (out / "home.html").write_text(snap.html, encoding="utf-8")
        await throttle.acquire()
        snap = await s.open(parser.REVIEW_URL.format(place_id=place_id))
        (out / "reviews.html").write_text(snap.html, encoding="utf-8")
        await s.page.screenshot(path=str(out / "screenshot.png"), full_page=True)
        payloads = list(snap.payloads)
        print("block check:", parser.detect_block(snap.url, snap.status, snap.html))
        for _ in range(more):
            if not await s.has_more():
                print("더보기 버튼 없음")
                break
            await throttle.acquire()
            snap = await s.click_more()
            payloads += snap.payloads
        for i, p in enumerate(payloads):
            (out / f"graphql_{i}.json").write_text(json.dumps(p, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"saved to {out} (graphql responses: {len(payloads)})")


def check(place_id: str, out: Path):
    today = today_kst()
    home = (out / "home.html").read_text(encoding="utf-8")
    print("place:", parser.parse_place_home(home, place_id))
    html = (out / "reviews.html").read_text(encoding="utf-8")
    apollo = parser.parse_apollo_reviews(parser.extract_apollo_state(html), today)
    dom = parser.parse_dom_reviews(html, today)
    print(f"apollo reviews: {len(apollo)}, dom reviews: {len(dom)}, container: {parser.has_review_container(html)}")
    for f in sorted(out.glob("graphql_*.json")):
        r = parser.parse_graphql_reviews(json.loads(f.read_text(encoding="utf-8")), today)
        if r is not None:
            print(f"{f.name}: {len(r)} reviews")
    for r in (apollo or dom)[:3]:
        print(" ", r.written_at, r.visited_at, r.rating, r.content[:40])


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("place_id")
    ap.add_argument("--more", type=int, default=1)
    ap.add_argument("--check", action="store_true")
    a = ap.parse_args()
    target = Path("dumps") / a.place_id
    if a.check:
        check(a.place_id, target)
    else:
        asyncio.run(dump(a.place_id, a.more, target))
        check(a.place_id, target)
