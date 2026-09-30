"""collector 설정 (환경변수)."""
import logging
import os
from dataclasses import dataclass

log = logging.getLogger("collector")

LOCAL_DEV_SALT = "local-dev-salt"


@dataclass(frozen=True)
class Settings:
    author_hash_salt: str
    min_interval_sec: float      # 요청(페이지 이동·더보기 클릭) 사이 최소 간격
    headless: bool
    page_timeout_ms: int
    fixture_dir: str | None      # 설정하면 네이버 대신 로컬 fixture 를 읽는다 (오프라인 리허설/테스트용)
    user_agent: str


def load_settings() -> Settings:
    salt = os.getenv("AUTHOR_HASH_SALT", "").strip()
    if not salt:
        if os.getenv("COLLECTOR_ENV", "local") != "local":
            raise RuntimeError("AUTHOR_HASH_SALT 환경변수가 필요합니다.")
        log.warning("AUTHOR_HASH_SALT 가 없어 개발용 salt 를 사용합니다 (backend local 프로필과 동일).")
        salt = LOCAL_DEV_SALT
    interval = float(os.getenv("COLLECTOR_MIN_INTERVAL_SEC", "2.0"))
    if interval < 2.0 and not os.getenv("COLLECTOR_FIXTURE_DIR"):
        # 실제 네이버에 요청할 때는 2초 미만으로 줄일 수 없다
        interval = 2.0
    return Settings(
        author_hash_salt=salt,
        min_interval_sec=interval,
        headless=os.getenv("COLLECTOR_HEADLESS", "true").lower() != "false",
        page_timeout_ms=int(os.getenv("COLLECTOR_PAGE_TIMEOUT_MS", "20000")),
        fixture_dir=os.getenv("COLLECTOR_FIXTURE_DIR") or None,
        user_agent=os.getenv(
            "COLLECTOR_USER_AGENT",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        ),
    )
