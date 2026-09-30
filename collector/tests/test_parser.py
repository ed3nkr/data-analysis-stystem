from datetime import date

import pytest

from app import parser

TODAY = date(2026, 9, 30)


@pytest.mark.parametrize("url,expected", [
    ("https://m.place.naver.com/restaurant/1234567/home", "1234567"),
    ("https://m.place.naver.com/restaurant/1234567/review/visitor?entry=ple", "1234567"),
    ("m.place.naver.com/restaurant/1234567", "1234567"),
    ("https://m.place.naver.com/place/1234567/home", "1234567"),
    ("https://pcmap.place.naver.com/restaurant/1234567/home", "1234567"),
    ("https://map.naver.com/p/entry/place/1234567?c=15.00,0,0,0,dh", "1234567"),
    ("https://map.naver.com/p/search/%EA%B9%80%EC%B9%98/place/1234567", "1234567"),
    ("https://map.naver.com/v5/entry/place/1234567", "1234567"),
    ("https://www.google.com/restaurant/1234567", None),
    ("https://m.place.naver.com/restaurant/abc/home", None),
    ("https://map.naver.com/p/entry/", None),
    ("hello", None),
])
def test_extract_place_id(url, expected):
    assert parser.extract_place_id(url) == expected


def test_short_link():
    assert parser.is_short_link("https://naver.me/5AbCdE")
    assert parser.is_short_link("naver.me/5AbCdE")
    assert not parser.is_short_link("https://map.naver.com/p/entry/place/1")


@pytest.mark.parametrize("text,expected", [
    ("9.28.일", date(2026, 9, 28)),
    ("9.28", date(2026, 9, 28)),
    ("12.31.목", date(2025, 12, 31)),        # 미래 날짜면 작년
    ("25.12.31.수", date(2025, 12, 31)),
    ("2026.8.5", date(2026, 8, 5)),
    ("2026-08-05T12:00:00+09:00", date(2026, 8, 5)),
    ("2026. 8. 5.", date(2026, 8, 5)),
    ("2.30.월", None),
    ("", None),
    (None, None),
    ("어제", None),
])
def test_parse_naver_date(text, expected):
    assert parser.parse_naver_date(text, TODAY) == expected


def test_graphql_reviews():
    payload = [{"data": {"other": 1}}, {"data": {"visitorReviews": {"items": [
        {"id": "r1", "body": " 맛있어요 ", "created": "9.28.일", "visited": "9.27.토", "rating": None,
         "author": {"nickname": "손님A"}},
        {"id": "r2", "body": "", "created": "9.28.일"},                  # 본문 없음 → 제외
        {"id": "r3", "body": "별로", "created": "이상한날짜"},             # 날짜 없음 → 제외
        {"id": "r4", "body": "보통", "created": "9.1.화", "rating": 4},
    ]}}}]
    reviews = parser.parse_graphql_reviews(payload, TODAY)
    assert [r.review_id for r in reviews] == ["r1", "r4"]
    assert reviews[0].content == "맛있어요"
    assert reviews[0].written_at == date(2026, 9, 28) and reviews[0].visited_at == date(2026, 9, 27)
    assert reviews[0].nickname == "손님A" and reviews[0].rating is None
    assert reviews[1].rating == 4.0
    assert parser.parse_graphql_reviews({"data": {"place": {}}}, TODAY) is None


def test_apollo_reviews_resolve_refs():
    html = ('<script>window.__APOLLO_STATE__ = {"VisitorReview:1": {"id": "1", "body": "좋아요", '
            '"created": "9.20.일", "author": {"__ref": "VisitorReviewAuthor:x"}}, '
            '"VisitorReviewAuthor:x": {"nickname": "닉"}, "Other:1": {}};</script>')
    state = parser.extract_apollo_state(html)
    reviews = parser.parse_apollo_reviews(state, TODAY)
    assert len(reviews) == 1 and reviews[0].nickname == "닉"


def test_dom_fallback():
    html = """<ul id="_review_list">
      <li><span class="pui__NMi-Dp">손님B</span><div class="pui__vn15t2"><a>국물이 진해요</a></div>
          <time>9.26.토</time><time>9.27.일</time></li>
      <li><div class="pui__vn15t2"><a>날짜 없음</a></div></li>
    </ul>"""
    reviews = parser.parse_dom_reviews(html, TODAY)
    assert len(reviews) == 1
    r = reviews[0]
    assert (r.content, r.nickname, r.visited_at, r.written_at) == \
        ("국물이 진해요", "손님B", date(2026, 9, 26), date(2026, 9, 27))


@pytest.mark.parametrize("url,status,html,blocked", [
    ("https://nid.naver.com/nidlogin.login", 200, "", True),
    ("https://m.place.naver.com/x", 429, "", True),
    ("https://m.place.naver.com/x", 200, "<body>자동입력 방지 문자를 입력해 주세요</body>", True),
    ("https://m.place.naver.com/x", 200, "<body>비정상적인 접근이 감지되었습니다</body>", True),
    ("https://m.place.naver.com/x", 200, "<body>리뷰 목록</body><script>var s='captcha';</script>", False),
    ("https://m.place.naver.com/x", 200, "<body>맛있어요</body>", False),
])
def test_detect_block(url, status, html, blocked):
    assert (parser.detect_block(url, status, html) is not None) == blocked


def test_place_home_from_apollo():
    html = ('<script>window.__APOLLO_STATE__ = {"PlaceDetailBase:99": {"name": "우리식당", '
            '"roadAddress": "서울 중구 1", "address": "서울 중구 옛주소"}};</script>')
    info = parser.parse_place_home(html, "99")
    assert (info.place_name, info.address) == ("우리식당", "서울 중구 1")


def test_place_home_from_og_meta():
    html = '<meta property="og:title" content="우리식당 : 네이버"><meta property="og:description" content="서울 중구 2">'
    info = parser.parse_place_home(html, "99")
    assert (info.place_name, info.address) == ("우리식당", "서울 중구 2")


def test_place_home_not_found():
    assert parser.parse_place_home('<meta property="og:title" content="네이버 지도">', "99") is None
    assert parser.parse_place_home("<html></html>", "99") is None


def test_real_naver_rate_limit_page_detected_without_status():
    """2026-09-30 클라우드 IP 로 접속했을 때 받은 실제 제한 페이지 (IP 가림)."""
    from pathlib import Path
    html = (Path(__file__).parent / "fixtures" / "naver_rate_limited.html").read_text(encoding="utf-8")
    assert parser.detect_block("https://m.place.naver.com/restaurant/1/home", None, html) is not None
    assert parser.detect_block("https://m.place.naver.com/restaurant/1/home", 429, html) is not None


def test_apollo_followed_by_other_assignments():
    """실제 페이지처럼 __APOLLO_STATE__ 뒤에 다른 window.__X__ 할당이 같은 스크립트에 이어지는 경우."""
    html = ('<script>window.__APOLLO_STATE__ = {"VisitorReview:1": {"id": "1", "body": "a};b", "created": "9.20.일"}};'
            'window.__PLACE_STATE__ = {"x": 1};</script>')
    state = parser.extract_apollo_state(html)
    assert list(state) == ["VisitorReview:1"]
    assert parser.parse_apollo_reviews(state, TODAY)[0].content == "a};b"


def test_real_place_not_found_page():
    """존재하지 않는 placeId 로 받은 실제 페이지의 구조 (placeDetail = null) 를 줄인 fixture."""
    from pathlib import Path
    html = (Path(__file__).parent / "fixtures" / "naver_place_not_found.html").read_text(encoding="utf-8")
    state = parser.extract_apollo_state(html)
    assert parser.parse_place_home(html, "11591410") is None
    assert parser.place_detail_is_null(state, "11591410")
    assert not parser.place_detail_is_null(state, "999")
