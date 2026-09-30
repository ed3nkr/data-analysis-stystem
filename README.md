# 리뷰-매출 연계 데이터분석 시스템 (1~4주차)

소상공인이 **매출 데이터**와 **네이버 플레이스 공개 리뷰**를 함께 보고, 매출 변화와 리뷰 불만의 연결을 해석받는 시스템(졸업 논문용).
이번 범위는 **모델 없이 동작하는 기반 기능**이다. 리뷰 분석(노이즈/부정 유형), 딥러닝 모델, 해석 규칙 엔진, 주간 리포트, 생성형 AI 요약은 **5주차 이후** 범위이며,
`review` 테이블의 분석 결과 컬럼은 모두 NULL 로 둔다.

```
/backend    Spring Boot 3.5 (Java 21) — API, 인증, 작업 관리
/collector  Python FastAPI + Playwright — 네이버 플레이스 공개 리뷰 수집 (5주차부터 분석 추가 예정)
/samples    시연용 가상 데이터 (sales_sample.csv, reviews_sample.csv, generate_samples.py)
docker-compose.yml  PostgreSQL 16
demo.sh / demo.http 시연 순서 스크립트
```

---

## 1. 실행 방법

### 1-1. 준비물
- Java 21, Python 3.11+, Docker (PostgreSQL 용)
- 환경변수 파일: `cp .env.example .env` 후 값 채우기 (`.env` 는 Git 에 올라가지 않는다)

### 1-2. PostgreSQL
```bash
docker compose up -d        # localhost:5432, DB/계정 기본값 reviewsales / app / app
```

### 1-3. backend
```bash
cd backend
set -a; source ../.env; set +a      # 환경변수 적용 (Spring 은 .env 를 직접 읽지 않는다)
./gradlew bootRun                   # 기본 프로필 = local (dev-login 활성화), http://localhost:8080
```
- Swagger UI: http://localhost:8080/swagger-ui.html (우측 상단 Authorize 에 액세스 토큰 입력)
- Flyway 가 `V1__init.sql` 로 테이블 7개(owner, store, menu, job, sales_record, review, insight)를 만든다.
- 테스트: `./gradlew test`
- 운영 프로필 예: `./gradlew bootRun -Dspring.profiles.active=prod` (이때 `JWT_SECRET`, `AUTHOR_HASH_SALT` 필수, dev-login 비활성)

### 1-4. collector
```bash
cd collector
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
playwright install chromium          # headless Chromium 설치 (최초 1회)
set -a; source ../.env; set +a
uvicorn app.main:app --port 8000     # 헬스 체크: GET http://localhost:8000/internal/v1/health
```
- 테스트: `python -m pytest -q`
- **오프라인 리허설 모드**: `COLLECTOR_FIXTURE_DIR=fixtures_demo uvicorn app.main:app --port 8000`
  → 네이버에 접속하지 않고 `fixtures_demo/` 의 **가상 데이터**(플레이스 ID `1234567890`, “가상 한식당 (fixture)”)로
  같은 파서·수집 로직을 돌린다. 발표장 네트워크가 막혔을 때 파이프라인 시연용이며, 이 데이터는 실제 리뷰가 아니다.

## 2. 환경변수

| 이름 | 사용처 | 설명 |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | backend | 기본 `jdbc:postgresql://localhost:5432/reviewsales`, `app`/`app` |
| `JWT_SECRET` | backend | HS256 키(32바이트 이상). local 프로필에서 비우면 기동 시 임시 키 생성 |
| `AUTHOR_HASH_SALT` | backend, collector | 작성자 닉네임 해시 salt. **두 서버가 같은 값**이어야 한다. local 에서 비우면 둘 다 `local-dev-salt` |
| `COLLECTOR_BASE_URL` | backend | 기본 `http://localhost:8000` |
| `COLLECT_MIN_INTERVAL` | backend | 같은 매장 재수집 제한, 기본 `1h` |
| `COLLECT_WEEKLY_ENABLED` | backend | 주간 자동 수집(매주 월 04:00) 켜기, 기본 `false` |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | backend | 없으면 카카오 로그인만 비활성 |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | backend | 없으면 구글 로그인만 비활성 |
| `OAUTH_REDIRECT_BASE` | backend | 콜백 주소 앞부분, 기본 `http://localhost:8080` |
| `FRONTEND_BASE_URL` | backend | 로그인 성공 후 `{값}/login/success` 로 이동, 기본 `http://localhost:3000` |
| `COOKIE_SECURE` | backend | HTTPS 배포 시 `true` |
| `COLLECTOR_MIN_INTERVAL_SEC` | collector | 요청 간 최소 간격(기본 2.0). 실제 네이버 대상일 때는 2초 미만으로 내려가지 않는다 |
| `COLLECTOR_FIXTURE_DIR` | collector | 설정 시 오프라인 fixture 모드 |
| `COLLECTOR_HEADLESS` | collector | `false` 면 브라우저 창을 띄운다 (디버깅) |

## 3. 시연 순서 (4주차 발표)

1. `docker compose up -d` → backend `./gradlew bootRun` → collector `uvicorn ...`
2. `./demo.sh` (또는 VS Code REST Client 로 `demo.http` 를 위에서부터 실행)
   1. 개발용 로그인 → 2. 매장 등록 → 3. 메뉴 등록 → 4. 매출 CSV 업로드 (9,140행 성공 / 오류 6행, 미등록 메뉴 후보 `계절한정 냉면`)
   → 4-1. 같은 기간 재업로드 409 → 4-2. 오류 행 조회 → 5. 플레이스 링크 미리보기·연결
   → 6. 리뷰 수집 요청(202) → 작업 상태 폴링 → 7. 리뷰 파일 등록(대체 수단, 중복 건너뜀)
   → 8. 리뷰 목록(분석 필드 null) → 9·10. 매출 일별/메뉴별 요약 → 11. 매장 상세·작업 이력
3. 실제 매장으로 시연: `PLACE_URL='https://m.place.naver.com/restaurant/<ID>/home' ./demo.sh`
   (collector 를 fixture 모드 없이 실행). 수집이 막히면 스크립트가 파일 등록으로 이어 간다.

> 시연 데이터(`samples/`, `collector/fixtures_demo/`)는 모두 **가상 데이터**다.
> `sales_sample.csv` 에는 8/10 이후 김치찌개 판매 감소, `reviews_sample.csv` 에는 같은 시기 김치찌개 불만 증가 패턴을 넣어 두었다 (5주차 이후 분석 시연용).

## 4. API 요약

공통 응답: 성공 `{"success": true, "data": ...}` / 실패 `{"success": false, "error": {"code", "message", "data"?}}`.
`/api/v1/stores/{storeId}/**` 는 본인 매장만 (아니면 403 `FORBIDDEN_STORE`, 없으면 404 `STORE_NOT_FOUND`).

| 메서드 | 경로 | 설명 |
|---|---|---|
| POST | `/api/v1/auth/dev-login` | **local 전용** `{email}` → 토큰 |
| POST | `/api/v1/auth/refresh` | 리프레시 쿠키 → `{accessToken, tokenType, expiresIn}` |
| POST | `/api/v1/auth/logout` | 쿠키 삭제, 204 |
| GET | `/api/v1/auth/oauth/{kakao\|google}` | 제공자 인증 페이지로 302 |
| GET | `/api/v1/auth/oauth/{provider}/callback` | 로그인 처리 후 프론트 `/login/success` 로 302 |
| GET | `/api/v1/me` | `{ownerId, provider, email, createdAt}` |
| POST/GET | `/api/v1/stores` | 매장 등록(201) / 목록 |
| GET/PATCH | `/api/v1/stores/{storeId}` | 상세(place, lastCollectedAt, menuCount) / 수정 |
| POST | `/api/v1/stores/{storeId}/place/preview` | `{placeUrl}` → `{placeId, placeName, address}` (저장 안 함) |
| PUT | `/api/v1/stores/{storeId}/place` | `{placeId}` → 연결 저장 |
| GET/POST | `/api/v1/stores/{storeId}/menus` | 메뉴 목록 / 등록 `{posName, aliases?}` |
| PATCH/DELETE | `/api/v1/stores/{storeId}/menus/{menuId}` | 수정 / 삭제 (매출 연결 시 409 `MENU_IN_USE`) |
| POST | `/api/v1/stores/{storeId}/sales/uploads` | multipart `file`, `columnMapping?`, `replace?` → 201 |
| GET | `/api/v1/stores/{storeId}/sales/uploads` | 업로드 이력 (페이지) |
| GET | `/api/v1/stores/{storeId}/sales/uploads/{uploadId}/errors` | 오류 행 목록 |
| GET | `/api/v1/stores/{storeId}/sales/summary?from&to&groupBy=DAY\|MENU` | 수량·금액 합계 |
| POST | `/api/v1/stores/{storeId}/reviews/uploads` | 리뷰 CSV 등록 → 202 |
| GET | `/api/v1/stores/{storeId}/reviews?from&to&page&size` | 리뷰 원문 (최신순) |
| POST | `/api/v1/stores/{storeId}/review-collections` | 네이버 리뷰 수집 요청 → 202 |
| GET | `/api/v1/jobs/{jobId}` | 작업 상태 |
| GET | `/api/v1/stores/{storeId}/jobs?type&page&size` | 작업 이력 |

collector (내부): `POST /internal/v1/places/resolve`, `POST /internal/v1/reviews/collect`, `GET /internal/v1/health`.

## 5. 구현 메모 (설계서에 없던 세부 결정)

- **추가 에러 코드** (공통 코드 외, 각 기능 명세의 코드와 함께 사용): `MENU_NOT_FOUND(404)`, `MENU_ALREADY_EXISTS(409)`, `MENU_IN_USE(409)`,
  `JOB_NOT_FOUND(404)`, `UPLOAD_NOT_FOUND(404)`, `CSV_EMPTY(400, 유효한 행 0개)`, `COLLECTOR_UNAVAILABLE(502)`, `NAVER_UNREACHABLE(502)`.
  남의 작업을 `GET /jobs/{id}` 로 조회하면 존재를 드러내지 않도록 404 로 응답한다.
- **리뷰 날짜**: 네이버 방문자 리뷰는 날짜 단위만 제공하므로 `written_at`, `visited_at` 은 `DATE`.
- **해시 규칙** (backend `ReviewHasher.java` = collector `hashing.py`, 같은 테스트 벡터로 검증):
  `author_hash = SHA-256(닉네임.strip() + salt)`(닉네임 없으면 NULL), `dedup_key = SHA-256("yyyy-MM-dd|author_hash 또는 빈값|content.strip() 앞 50자")`
  (50자는 유니코드 코드포인트 기준). 닉네임 원문은 collector 에서 해시 후 버리며 DB 에 저장하지 않는다.
- **매출 업로드**: 요청 안에서 동기 처리(201). 행 번호는 파일 기준(헤더=1행). 수량은 1 이상 정수, 금액은 0 이상 정수(`,` `원` 허용).
  판매일시는 `yyyy-MM-dd HH:mm[:ss]`, `/`·`.` 구분자, `T` 구분, `yyyyMMddHHmmss`, 날짜만 등 허용.
  기간 중복 판정은 완료된 SALES_UPLOAD 작업의 `period_start~period_end` 와 겹치는지로 한다. 업로드 실패(열 누락·인코딩 등)도 job 이력에 FAILED 로 남는다.
- **리뷰 파일 등록**: 요청 안에서 동기 처리하고 202 로 결과를 준다. 응답에 `errorRows` 를 추가로 넣었다(오류 행은 job.error_detail).
- **리뷰 수집**: `since` = `last_collected_at` 의 날짜(없으면 6개월 전). 같은 날 리뷰는 dedup 으로 걸러진다. 성공 시 `last_collected_at` = 요청 시각.
  `maxRequests` 도달로 끝나도 COMPLETED 처리한다(로그에 중단 사유 기록). 서버 재시작 시 멈춘 작업은 FAILED(`INTERRUPTED`)로 정리한다.
- **OAuth state**: 테이블 없이 서명된 JWT(10분) + 같은 nonce 를 HttpOnly 쿠키(`oauth_state`)에 넣어 콜백에서 대조한다.
  콜백은 리프레시 쿠키만 심고, 프론트가 `/auth/refresh` 로 액세스 토큰을 받는다.

## 6. 네이버 파서 점검 방법 (중요)

네이버 플레이스는 클래스명이 난독화되어 있고 자주 바뀐다. 파싱 코드는 전부 `collector/app/parser.py` 에 있으며,
리뷰는 ① 더보기 시 호출되는 GraphQL 응답(`visitorReviews.items`) → ② 첫 화면의 `window.__APOLLO_STATE__` → ③ DOM 셀렉터 순으로 읽는다.

**클라우드 개발 환경에서 확인한 것 (2026-09-30)**
- 클라우드 서버 IP 로는 네이버가 첫 요청부터 `429 "과도한 접근 요청으로 서비스 이용이 제한되었습니다"` 또는
  지도 검색의 캡차(`ncaptcha`)로 응답했다 → 수집기는 이를 `ACCESS_BLOCKED` 로 감지해 즉시 중단한다 (실제 제한 페이지를 테스트 fixture 로 보관).
- 존재하지 않는 placeId 의 실제 페이지는 `__APOLLO_STATE__.ROOT_QUERY["placeDetail({...\"id\":\"<id>\"...})"] = null` 이다 → `PLACE_NOT_FOUND`.
- `__APOLLO_STATE__` 뒤에 `__PLACE_STATE__` 등 다른 할당이 이어지므로 JSON 디코더로 읽도록 수정했다.
- **실제 매장의 리뷰 페이지 구조는 아직 확인하지 못했다.** 클라우드 IP 는 제한되므로 **본인 PC(가정/학교 네트워크)** 에서 확인해야 한다:
```bash
cd collector && source .venv/bin/activate
python tools/dump_page.py <실제 placeId> --more 1
# dumps/<placeId>/ 에 home.html, reviews.html, graphql_*.json, screenshot.png 저장 후 파싱 결과를 출력
```
출력에서 `apollo reviews` 또는 `graphql_*.json: N reviews` 가 0 이면 저장된 파일을 보고 `parser.py` 의 필드명/`SELECTORS` 를 고친 뒤
`python tools/dump_page.py <placeId> --check` 로 다시 확인한다 (재요청 없이 저장본만 파싱).

## 7. OAuth 앱 등록 (카카오·구글)

콜백 주소: `{OAUTH_REDIRECT_BASE}/api/v1/auth/oauth/{kakao|google}/callback` (로컬: `http://localhost:8080/api/v1/auth/oauth/kakao/callback`)

**카카오** (https://developers.kakao.com)
1. 내 애플리케이션 → 애플리케이션 추가
2. 앱 키의 **REST API 키** → `KAKAO_CLIENT_ID`
3. 카카오 로그인 → 활성화 ON, **Redirect URI** 에 위 콜백 주소 등록
4. 보안 → Client Secret 코드 생성·활성화 → `KAKAO_CLIENT_SECRET` (사용 안 하면 비워 둠)
5. 동의항목 → 카카오계정(이메일) 선택 동의 설정 (이메일 없이도 로그인은 된다)

**구글** (https://console.cloud.google.com)
1. 프로젝트 생성 → API 및 서비스 → OAuth 동의 화면 구성 (테스트 사용자에 본인 계정 추가)
2. 사용자 인증 정보 → OAuth 클라이언트 ID 만들기 → 유형 **웹 애플리케이션**
3. 승인된 리디렉션 URI 에 위 콜백 주소(`.../oauth/google/callback`) 등록
4. 클라이언트 ID/보안 비밀 → `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` (scope: `openid email`)

브라우저에서 `http://localhost:8080/api/v1/auth/oauth/kakao` 로 들어가면 로그인 후 `FRONTEND_BASE_URL/login/success` 로 돌아온다.

## 8. 구현 범위와 이후 계획

| 주차 | 내용 |
|---|---|
| 1~4 (이번) | DB 스키마, 인증(JWT·dev-login·OAuth), 매장/메뉴, 매출 CSV 업로드, 리뷰 파일 등록, 네이버 공개 리뷰 수집, 조회 API |
| 5~ | 리뷰 라벨링 → collector 에 딥러닝 분석 추가(노이즈 신뢰도, 부정 유형, 메뉴·시간대 매칭) → `review` 분석 컬럼 채움(ANALYZE 작업) |
| 이후 | 해석 규칙 엔진(`insight`), 주간 리포트, 생성형 AI 요약 |
