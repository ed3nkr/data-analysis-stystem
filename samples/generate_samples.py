"""시연용 가상 데이터 생성기 (실제 매장 데이터가 아님).

    python samples/generate_samples.py

- sales_sample.csv   : 한식당 3개월(2026-06-01 ~ 2026-08-31) 가상 매출. 점심·저녁 피크,
                       (1인)/(ICE) 같은 옵션 표기, 등록 안 된 메뉴, 일부러 넣은 오류 행 포함.
                       8월 둘째 주부터 김치찌개 판매가 줄어드는 패턴을 넣어 두었다 (이후 리뷰 연계 분석 시연용).
- reviews_sample.csv : 가상 리뷰 50건 (파일 등록 시연용). 8월 중순부터 김치찌개 관련 불만이 늘어난다.
"""
import csv
import random
from datetime import date, datetime, timedelta
from pathlib import Path

OUT = Path(__file__).resolve().parent
rng = random.Random(20260930)

# (POS 표기, 단가, 점심 가중치, 저녁 가중치)
MENUS = [
    ("김치찌개(1인)", 9000, 10, 6),
    ("김치찌개(2인)", 17000, 3, 5),
    ("된장찌개(1인)", 9000, 7, 4),
    ("순두부찌개", 9500, 6, 3),
    ("제육볶음", 11000, 6, 7),
    ("불고기 정식", 13000, 4, 6),
    ("비빔밥", 9000, 6, 3),
    ("갈비탕", 13000, 3, 5),
    ("계란말이", 7000, 2, 6),
    ("공기밥", 1000, 5, 5),
    ("아메리카노(ICE)", 2000, 4, 1),
    ("아메리카노(HOT)", 2000, 2, 1),
    ("콜라", 2000, 2, 4),
    ("계절한정 냉면", 10000, 3, 2),   # 메뉴 등록 안 함 → newMenuCandidates
]


def pick(weights_idx):
    weights = [m[weights_idx] for m in MENUS]
    return rng.choices(MENUS, weights=weights, k=1)[0]


def sales_rows():
    rows = []
    d = date(2026, 6, 1)
    while d <= date(2026, 8, 31):
        weekday = d.weekday()
        closed = weekday == 6 and d.day <= 7  # 매월 첫째 일요일 휴무
        if not closed:
            lunch_orders = rng.randint(28, 40) if weekday < 5 else rng.randint(18, 26)
            dinner_orders = rng.randint(20, 30) if weekday < 5 else rng.randint(26, 36)
            for slot, n, widx in (("L", lunch_orders, 2), ("D", dinner_orders, 3)):
                for _ in range(n):
                    if slot == "L":
                        t = datetime(d.year, d.month, d.day, 11, 20) + timedelta(minutes=int(rng.triangular(0, 150, 50)))
                    else:
                        t = datetime(d.year, d.month, d.day, 17, 30) + timedelta(minutes=int(rng.triangular(0, 210, 90)))
                    t = t.replace(second=rng.randint(0, 59))
                    for _ in range(rng.choice([1, 1, 2, 2, 3])):
                        name, price, *_ = pick(widx)
                        # 8/10 이후 김치찌개 판매 감소 (가상 시나리오)
                        if name.startswith("김치찌개") and d >= date(2026, 8, 10) and rng.random() < 0.45:
                            name, price, *_ = MENUS[2]
                        if name == "계절한정 냉면" and d.month == 6:
                            continue
                        qty = 1 if rng.random() < 0.8 else 2
                        rows.append([t.strftime("%Y-%m-%d %H:%M:%S"), name, qty, price * qty])
        d += timedelta(days=1)
    rows.sort(key=lambda r: r[0])

    # 일부러 넣은 오류 행 (파서 오류 처리 시연)
    bad = [
        ["2026-06-31 12:10:00", "김치찌개(1인)", 1, 9000],      # 없는 날짜
        ["2026/07/02 12:30", "제육볶음", "하나", 11000],          # 수량 형식 오류 (날짜 형식은 허용)
        ["2026-07-15 13:00:00", "", 1, 9000],                   # 상품명 없음
        ["2026-08-03 18:20:00", "갈비탕", 1, "만삼천원"],          # 금액 형식 오류
        ["어제 저녁", "비빔밥", 1, 9000],                         # 날짜 형식 오류
        ["2026-08-20 19:00:00", "콜라", 0, 0],                   # 수량 0
    ]
    for b in bad:
        rows.insert(rng.randint(1, len(rows) - 1), b)
    return rows


def write_sales():
    rows = sales_rows()
    path = OUT / "sales_sample.csv"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["판매일시", "상품명", "수량", "결제금액"])
        w.writerows(rows)
    print(f"{path.name}: {len(rows)} rows")


REVIEWS_OK = [
    "점심에 김치찌개 먹었는데 국물이 진하고 좋아요. 반찬도 깔끔합니다.",
    "제육볶음 양이 많고 맛있어요. 직원분들도 친절하세요.",
    "된장찌개가 집밥 느낌이라 자주 옵니다.",
    "불고기 정식 구성이 알차요. 가격 대비 만족합니다.",
    "갈비탕 고기가 부드럽고 국물이 깔끔해요.",
    "계란말이 폭신하고 맛있어요! 저녁에 반주하기 좋아요.",
    "비빔밥 나물이 신선해서 좋았습니다.",
    "순두부찌개 적당히 매콤하고 맛있네요.",
    "점심시간에도 음식이 빨리 나와서 좋았어요.",
    "매장이 깨끗하고 분위기 좋아요. 재방문 의사 있습니다.",
    "김치찌개 2인 시켰는데 고기가 넉넉하게 들어있어요.",
    "직장 근처라 자주 오는데 늘 맛이 일정해요.",
]
REVIEWS_BAD_EARLY = [
    "점심시간에 웨이팅이 좀 길었어요. 맛은 괜찮습니다.",
    "에어컨이 약해서 조금 더웠어요.",
]
REVIEWS_BAD_LATE = [
    "김치찌개가 예전보다 많이 싱거워졌어요. 고기도 줄어든 느낌이에요.",
    "김치찌개 맛이 변했네요... 예전 맛이 그립습니다.",
    "김치찌개 양이 줄었어요. 가격은 그대로인데 아쉽네요.",
    "김치찌개 국물이 밍밍해요. 다른 메뉴는 괜찮았어요.",
    "요즘 김치찌개에 김치가 덜 익은 것 같아요.",
    "저녁에 갔는데 김치찌개가 너무 늦게 나왔어요.",
]
NICKNAMES = ["맛집탐방러", "점심요정", "hungry_kim", "직장인A", "동네주민", "먹깨비", "sora", "밥심",
             "리뷰왕", "jjm0412", "한식러버", "퇴근길", "배고파요", "yummy", "산책하는곰"]


def write_reviews():
    rows = []
    start = date(2026, 6, 3)
    for i in range(50):
        visited = start + timedelta(days=int(i * 88 / 50) + rng.randint(0, 1))
        written = visited + timedelta(days=rng.choice([0, 0, 1, 1, 2]))
        late = visited >= date(2026, 8, 10)
        r = rng.random()
        if late and r < 0.55:
            content, rating = rng.choice(REVIEWS_BAD_LATE), rng.choice(["2", "3", "3"])
        elif r < 0.12:
            content, rating = rng.choice(REVIEWS_BAD_EARLY), rng.choice(["3", "4"])
        else:
            content, rating = rng.choice(REVIEWS_OK), rng.choice(["4", "5", "5", ""])
        rows.append([written.isoformat(), content, visited.isoformat(), rating, rng.choice(NICKNAMES)])
    # 완전히 같은 리뷰 1건 (중복 건너뜀 시연), 필수값 없는 행 1건
    rows.append(list(rows[10]))
    rows.append(["", "작성일이 없는 행", "2026-07-01", "5", "누군가"])
    path = OUT / "reviews_sample.csv"
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f)
        w.writerow(["writtenAt", "content", "visitedAt", "rating", "author"])
        w.writerows(rows)
    print(f"{path.name}: {len(rows)} rows (정상 50 + 중복 1 + 오류 1)")


if __name__ == "__main__":
    write_sales()
    write_reviews()
