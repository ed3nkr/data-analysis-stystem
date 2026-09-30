"""오프라인 리허설/테스트용 '가상' fixture 생성 (실제 네이버 데이터가 아님).

    python tools/make_demo_fixtures.py

구조는 parser.py 가 읽는 형태(__APOLLO_STATE__, GraphQL visitorReviews)를 흉내 낸 것이다.
실제 페이지 구조 확인은 tools/dump_page.py 로 해야 한다.
"""
import json
import random
from datetime import date, timedelta
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "fixtures_demo"
PLACE_ID = "1234567890"
WEEKDAYS = "월화수목금토일"
rng = random.Random(7)

TEXTS = [
    "김치찌개 국물이 진하고 맛있어요", "제육볶음 양이 많아요", "직원분들이 친절해요",
    "김치찌개가 예전보다 싱거워졌어요", "웨이팅이 길었어요", "된장찌개 집밥 느낌 좋아요",
    "김치찌개 고기가 줄었어요", "매장이 깨끗해요", "음식이 빨리 나와요", "갈비탕 추천합니다",
]


def naver_date(d: date) -> str:
    return f"{d.month}.{d.day}.{WEEKDAYS[d.weekday()]}"


def make_reviews(n: int, start: date):
    items, d = [], start
    for i in range(n):
        d -= timedelta(days=rng.choice([1, 2, 3]))
        visited = d - timedelta(days=rng.choice([0, 1]))
        items.append({
            "__typename": "VisitorReview", "id": f"fx{i:04d}",
            "body": f"[가상] {rng.choice(TEXTS)}", "rating": None,
            "created": naver_date(d), "visited": naver_date(visited),
            "author": {"__typename": "VisitorReviewAuthor", "id": f"a{i % 9}", "nickname": f"가상손님{i % 9}"},
        })
    return items


def main():
    OUT.mkdir(exist_ok=True)
    reviews = make_reviews(40, date(2026, 9, 30))
    first, rest = reviews[:10], reviews[10:]
    state = {"ROOT_QUERY": {}}
    for r in first:
        author = r["author"]
        state[f"VisitorReviewAuthor:{author['id']}"] = author
        state[f"VisitorReview:{r['id']}"] = {**r, "author": {"__ref": f"VisitorReviewAuthor:{author['id']}"}}
    html = (
        "<!doctype html><html><head><meta charset='utf-8'><title>가상 한식당 (fixture)</title></head><body>"
        "<div class='place_section_content'><ul id='_review_list'></ul><a class='fvwqf'>더보기</a></div>"
        f"<script>window.__APOLLO_STATE__ = {json.dumps(state, ensure_ascii=False)};</script>"
        "</body></html>")
    (OUT / f"reviews_{PLACE_ID}.html").write_text(html, encoding="utf-8")
    for n, i in enumerate(range(0, len(rest), 10), start=1):
        payload = [{"data": {"visitorReviews": {"items": rest[i:i + 10], "total": len(reviews)}}}]
        (OUT / f"reviews_{PLACE_ID}_more{n}.json").write_text(json.dumps(payload, ensure_ascii=False, indent=1),
                                                             encoding="utf-8")
    home_state = {f"PlaceDetailBase:{PLACE_ID}": {"__typename": "PlaceDetailBase", "id": PLACE_ID,
                                                 "name": "가상 한식당 (fixture)",
                                                 "roadAddress": "서울특별시 가상구 예시로 12"}}
    home = ("<!doctype html><html><head><meta property='og:title' content='가상 한식당 (fixture) : 네이버'></head>"
            f"<body><script>window.__APOLLO_STATE__ = {json.dumps(home_state, ensure_ascii=False)};</script></body></html>")
    (OUT / f"place_{PLACE_ID}.html").write_text(home, encoding="utf-8")
    print("written to", OUT)


if __name__ == "__main__":
    main()
