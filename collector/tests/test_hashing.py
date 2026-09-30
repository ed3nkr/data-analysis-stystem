"""backend ReviewHasherTest 와 같은 벡터 — 두 구현의 결과가 같아야 dedup 이 맞는다."""
from datetime import date

from app.hashing import author_hash, dedup_key

DAY = date(2026, 8, 15)


def test_author_hash():
    assert author_hash("맛집탐방러", "salt123") == "fa6459ba378232302a2a89b5f9f41b6ac490e30c5ebf37bbfbaee48fcbbce27c"
    assert author_hash("  맛집탐방러 ", "salt123") == author_hash("맛집탐방러", "salt123")
    assert author_hash("", "salt123") is None
    assert author_hash(None, "salt123") is None


def test_dedup_key():
    a = author_hash("맛집탐방러", "salt123")
    content = "김치찌개가 예전보다 많이 싱거워졌어요. 고기도 줄어든 느낌이에요. 다음엔 다른 메뉴를 먹어볼게요 😀 끝"
    assert dedup_key(DAY, a, content) == "e3783c1c9e488865970c0ece21eabf992d55bf43b15afa13742f35573004e434"
    assert dedup_key(DAY, None, "짧은 리뷰") == "86eaf425f5a2dca8ad2c4eb4721a178425c99c060e327dee1fad46bbb3f6bdeb"
    assert dedup_key(DAY, None, "😀" * 60) == "288dc86a77ac29b93d5912063b42bc9b17a24d6b46df93adffe8da13b6a7398b"
