import asyncio
from datetime import date
from pathlib import Path

import pytest

from app import naver
from app.config import Settings
from app.errors import AccessBlocked, PageStructureChanged
from app.hashing import author_hash

FIXTURES = str(Path(__file__).resolve().parents[1] / "fixtures_demo")
PLACE = "1234567890"


def settings(fixture_dir=FIXTURES):
    return Settings(author_hash_salt="s", min_interval_sec=2.0, headless=True, page_timeout_ms=1000,
                    fixture_dir=fixture_dir, user_agent="test")


class FakeClock:
    """실제로 기다리지 않고 sleep 시간을 기록한다."""

    def __init__(self):
        self.now = 100.0
        self.request_times = []

    def clock(self):
        return self.now

    async def sleep(self, sec):
        self.now += sec


def throttle(max_requests, clock: FakeClock):
    t = naver.Throttle(2.0, max_requests, clock=clock.clock, sleep=clock.sleep)
    original = t.acquire

    async def acquire():
        await original()
        clock.request_times.append(clock.now)
    t.acquire = acquire
    return t


def run(coro):
    return asyncio.run(coro)


def test_collect_all_until_end_with_2s_spacing(monkeypatch):
    monkeypatch.setattr(naver, "today_kst", lambda: date(2026, 9, 30))
    c = FakeClock()
    r = run(naver.collect_reviews(settings(), PLACE, date(2020, 1, 1), 200, throttle=throttle(200, c)))
    assert r.stopped_reason == "END_OF_LIST"
    assert r.request_count == 4                     # 첫 화면 + 더보기 3번
    assert len(r.reviews) == 40
    gaps = [b - a for a, b in zip(c.request_times, c.request_times[1:])]
    assert all(g >= 2.0 for g in gaps)
    dates = [x["writtenAt"] for x in r.reviews]
    assert dates == sorted(dates, reverse=True)      # 최신순
    first = r.reviews[0]
    assert set(first) == {"writtenAt", "visitedAt", "rating", "content", "authorHash", "dedupKey"}
    assert "nickname" not in str(first) and "가상손님" not in str(first)   # 닉네임 원문 미반환
    assert first["authorHash"] == author_hash("가상손님0", "s")


def test_stops_when_reaching_since(monkeypatch):
    monkeypatch.setattr(naver, "today_kst", lambda: date(2026, 9, 30))
    c = FakeClock()
    since = date(2026, 9, 1)
    r = run(naver.collect_reviews(settings(), PLACE, since, 200, throttle=throttle(200, c)))
    assert r.stopped_reason == "REACHED_SINCE"
    assert r.request_count < 4
    assert r.reviews and all(x["writtenAt"] >= since.isoformat() for x in r.reviews)


def test_stops_at_max_requests(monkeypatch):
    monkeypatch.setattr(naver, "today_kst", lambda: date(2026, 9, 30))
    r = run(naver.collect_reviews(settings(), PLACE, date(2020, 1, 1), 2, throttle=throttle(2, FakeClock())))
    assert r.stopped_reason == "MAX_REQUESTS"
    assert r.request_count == 2
    assert len(r.reviews) == 20


def test_access_blocked_stops_immediately(tmp_path):
    (tmp_path / f"reviews_{PLACE}.html").write_text("<body>자동입력 방지 문자를 입력하세요</body>", encoding="utf-8")
    (tmp_path / f"reviews_{PLACE}_more1.json").write_text("[]", encoding="utf-8")
    c = FakeClock()
    with pytest.raises(AccessBlocked):
        run(naver.collect_reviews(settings(str(tmp_path)), PLACE, date(2020, 1, 1), 200, throttle=throttle(200, c)))
    assert len(c.request_times) == 1                # 재시도 없음


def test_structure_changed(tmp_path):
    (tmp_path / f"reviews_{PLACE}.html").write_text("<body><div>전혀 다른 페이지</div></body>", encoding="utf-8")
    with pytest.raises(PageStructureChanged):
        run(naver.collect_reviews(settings(str(tmp_path)), PLACE, date(2020, 1, 1), 200,
                                  throttle=throttle(200, FakeClock())))


def test_no_reviews_is_not_structure_change(tmp_path):
    (tmp_path / f"reviews_{PLACE}.html").write_text("<body>아직 리뷰가 없습니다</body>", encoding="utf-8")
    r = run(naver.collect_reviews(settings(str(tmp_path)), PLACE, date(2020, 1, 1), 200,
                                  throttle=throttle(200, FakeClock())))
    assert r.reviews == [] and r.stopped_reason == "END_OF_LIST"


def test_throttle_real_sleep_is_at_least_min_interval():
    slept = []

    async def fake_sleep(s):
        slept.append(s)
    t = naver.Throttle(2.0, 3, clock=lambda: 0.0, sleep=fake_sleep)

    async def go():
        for _ in range(3):
            await t.acquire()
        with pytest.raises(naver.MaxRequestsReached):
            await t.acquire()
    run(go())
    assert slept == [2.0, 2.0]
