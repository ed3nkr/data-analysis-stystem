class CollectorError(Exception):
    """API 로 그대로 내려가는 오류. code 는 backend ErrorCode 이름과 같다."""

    status = 500
    code = "INTERNAL_ERROR"

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class InvalidPlaceUrl(CollectorError):
    status, code = 400, "INVALID_PLACE_URL"


class PlaceNotFound(CollectorError):
    status, code = 404, "PLACE_NOT_FOUND"


class AccessBlocked(CollectorError):
    """로그인·캡차·접근 제한 감지. 재시도하지 않고 즉시 중단한다."""
    status, code = 503, "ACCESS_BLOCKED"


class PageStructureChanged(CollectorError):
    status, code = 422, "PAGE_STRUCTURE_CHANGED"


class Busy(CollectorError):
    status, code = 409, "JOB_ALREADY_RUNNING"


class NaverUnreachable(CollectorError):
    """네트워크 문제로 네이버에 연결하지 못함 (차단 감지와는 다름)."""
    status, code = 502, "NAVER_UNREACHABLE"
