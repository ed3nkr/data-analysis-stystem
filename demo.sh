#!/usr/bin/env bash
# 4주차 시연 스크립트: 로그인 → 매장 등록 → 메뉴 등록 → 매출 업로드 → 플레이스 연결 → 리뷰 수집
#                     → 작업 상태 확인 → 리뷰·매출 조회
#
#   ./demo.sh                                  # 기본값 (collector fixture 모드용 가상 플레이스)
#   PLACE_URL='https://m.place.naver.com/restaurant/<id>/home' ./demo.sh   # 실제 플레이스
#
# 필요: backend(local 프로필, :8080), collector(:8000), curl, python3
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/api/v1}"
EMAIL="${EMAIL:-demo-$(date +%H%M%S)@example.com}"   # 매 실행마다 새 사장님 (1시간 수집 제한 회피)
PLACE_URL="${PLACE_URL:-https://m.place.naver.com/restaurant/1234567890/home}"
ROOT="$(cd "$(dirname "$0")" && pwd)"

step() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }
pretty() { python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin), ensure_ascii=False, indent=2))'; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval('d' + sys.argv[1]))" "$1"; }

step "1. 개발용 로그인 (local 프로필 전용) — $EMAIL"
LOGIN=$(curl -sf -X POST "$BASE_URL/auth/dev-login" -H 'Content-Type: application/json' -d "{\"email\":\"$EMAIL\"}")
TOKEN=$(echo "$LOGIN" | field "['data']['accessToken']")
AUTH="Authorization: Bearer $TOKEN"
curl -s "$BASE_URL/me" -H "$AUTH" | pretty

step "2. 매장 등록"
STORE=$(curl -s -X POST "$BASE_URL/stores" -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"시연 한식당","category":"KOREAN"}')
echo "$STORE" | pretty
STORE_ID=$(echo "$STORE" | field "['data']['storeId']")

step "3. 메뉴 등록 (정규화 이름은 서버가 생성)"
for m in '{"posName":"김치찌개(1인)","aliases":["김치 찌개"]}' '{"posName":"된장찌개"}' '{"posName":"순두부찌개"}' \
         '{"posName":"제육볶음"}' '{"posName":"불고기 정식"}' '{"posName":"비빔밥"}' '{"posName":"갈비탕"}' \
         '{"posName":"계란말이"}' '{"posName":"공기밥"}' '{"posName":"아메리카노(ICE)","aliases":["아아"]}' '{"posName":"콜라"}'; do
  curl -s -X POST "$BASE_URL/stores/$STORE_ID/menus" -H "$AUTH" -H 'Content-Type: application/json' -d "$m" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin)["data"]; print("  %3s  %-16s -> %s" % (d["menuId"], d["posName"], d["normalizedName"]))'
done

step "4. 매출 CSV 업로드 (samples/sales_sample.csv)"
curl -s -X POST "$BASE_URL/stores/$STORE_ID/sales/uploads" -H "$AUTH" -F "file=@$ROOT/samples/sales_sample.csv" | tee /tmp/demo_upload.json | pretty
UPLOAD_ID=$(field "['data']['uploadId']" < /tmp/demo_upload.json)

step "4-1. 같은 파일 다시 업로드 → 기간 중복 409"
curl -s -X POST "$BASE_URL/stores/$STORE_ID/sales/uploads" -H "$AUTH" -F "file=@$ROOT/samples/sales_sample.csv" | pretty

step "4-2. 오류 행 확인"
curl -s "$BASE_URL/stores/$STORE_ID/sales/uploads/$UPLOAD_ID/errors" -H "$AUTH" | pretty

step "5. 네이버 플레이스 링크 미리보기 → 연결 ($PLACE_URL)"
PREVIEW=$(curl -s -X POST "$BASE_URL/stores/$STORE_ID/place/preview" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"placeUrl\":\"$PLACE_URL\"}")
echo "$PREVIEW" | pretty
COLLECTED=false
if echo "$PREVIEW" | grep -q '"success":true'; then
  PLACE_ID=$(echo "$PREVIEW" | field "['data']['placeId']")
  curl -s -X PUT "$BASE_URL/stores/$STORE_ID/place" -H "$AUTH" -H 'Content-Type: application/json' \
    -d "{\"placeId\":\"$PLACE_ID\"}" | pretty

  step "6. 리뷰 수집 요청 (비동기) → 작업 상태 폴링"
  JOB=$(curl -s -X POST "$BASE_URL/stores/$STORE_ID/review-collections" -H "$AUTH")
  echo "$JOB" | pretty
  JOB_ID=$(echo "$JOB" | field "['data']['jobId']")
  for _ in $(seq 1 600); do
    STATUS=$(curl -s "$BASE_URL/jobs/$JOB_ID" -H "$AUTH")
    S=$(echo "$STATUS" | field "['data']['status']")
    printf '  status=%s\n' "$S"
    [[ "$S" == "COMPLETED" || "$S" == "FAILED" ]] && break
    sleep 2
  done
  echo "$STATUS" | pretty
  [[ "$S" == "COMPLETED" ]] && COLLECTED=true
fi

if [[ "$COLLECTED" != true ]]; then
  step "6-1. 수집 실패 → 대체 수단: 리뷰 파일 등록 (samples/reviews_sample.csv)"
fi
step "7. 리뷰 파일 등록 (중복은 건너뜀)"
curl -s -X POST "$BASE_URL/stores/$STORE_ID/reviews/uploads" -H "$AUTH" -F "file=@$ROOT/samples/reviews_sample.csv" | pretty

step "8. 리뷰 원문 목록 (최신순, 분석 결과 필드는 아직 null)"
curl -s "$BASE_URL/stores/$STORE_ID/reviews?size=3" -H "$AUTH" | pretty

step "9. 매출 요약 — 일별 (2026-08-01 ~ 2026-08-07)"
curl -s "$BASE_URL/stores/$STORE_ID/sales/summary?from=2026-08-01&to=2026-08-07&groupBy=DAY" -H "$AUTH" | pretty

step "10. 매출 요약 — 메뉴별 (8월)"
curl -s "$BASE_URL/stores/$STORE_ID/sales/summary?from=2026-08-01&to=2026-08-31&groupBy=MENU" -H "$AUTH" | pretty

step "11. 매장 상세 / 작업 이력"
curl -s "$BASE_URL/stores/$STORE_ID" -H "$AUTH" | pretty
curl -s "$BASE_URL/stores/$STORE_ID/jobs?size=10" -H "$AUTH" \
  | python3 -c 'import json,sys; [print("  #%-4s %-15s %-10s processed=%s errors=%s" % (j["jobId"], j["type"], j["status"], j["processedCount"], j["errorCount"])) for j in json.load(sys.stdin)["data"]["content"]]'

printf '\n\033[1;32m시연 완료 (storeId=%s)\033[0m\n' "$STORE_ID"
