package com.reviewsales.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "토큰이 만료되었습니다."),
    FORBIDDEN_STORE(HttpStatus.FORBIDDEN, "본인 소유 매장만 접근할 수 있습니다."),
    STORE_NOT_FOUND(HttpStatus.NOT_FOUND, "매장을 찾을 수 없습니다."),
    JOB_ALREADY_RUNNING(HttpStatus.CONFLICT, "진행 중인 작업이 있습니다."),
    COLLECT_TOO_FREQUENT(HttpStatus.TOO_MANY_REQUESTS, "리뷰 수집은 매장당 1시간에 한 번만 요청할 수 있습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // 인증 / OAuth
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않거나 설정되지 않은 로그인 제공자입니다."),
    INVALID_STATE(HttpStatus.BAD_REQUEST, "OAuth state 값이 올바르지 않습니다."),
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "소셜 로그인에 실패했습니다."),

    // 메뉴
    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."),
    MENU_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 이름(정규화 기준)의 메뉴가 이미 있습니다."),
    MENU_IN_USE(HttpStatus.CONFLICT, "매출 기록이 연결된 메뉴는 삭제할 수 없습니다."),

    // 작업
    JOB_NOT_FOUND(HttpStatus.NOT_FOUND, "작업을 찾을 수 없습니다."),

    // 매출 업로드
    SALES_PERIOD_OVERLAP(HttpStatus.CONFLICT, "기존 업로드와 기간이 겹칩니다. replace=true 로 다시 요청하면 겹치는 기간을 교체합니다."),
    CSV_MISSING_COLUMNS(HttpStatus.BAD_REQUEST, "CSV 에 필수 열이 없습니다."),
    CSV_ENCODING_UNSUPPORTED(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 인코딩입니다. (UTF-8, EUC-KR 지원)"),
    CSV_EMPTY(HttpStatus.BAD_REQUEST, "CSV 에 데이터 행이 없습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "파일은 최대 10MB 까지 업로드할 수 있습니다."),
    UPLOAD_NOT_FOUND(HttpStatus.NOT_FOUND, "업로드 이력을 찾을 수 없습니다."),

    // 플레이스 / 수집
    PLACE_NOT_CONNECTED(HttpStatus.CONFLICT, "네이버 플레이스가 연결되지 않았습니다."),
    INVALID_PLACE_URL(HttpStatus.BAD_REQUEST, "지원하지 않는 플레이스 링크입니다."),
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "플레이스를 찾을 수 없습니다."),
    COLLECTOR_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "수집 서버에 연결할 수 없습니다."),
    NAVER_UNREACHABLE(HttpStatus.BAD_GATEWAY, "네이버에 연결하지 못했습니다."),
    ACCESS_BLOCKED(HttpStatus.SERVICE_UNAVAILABLE, "네이버 접근이 제한되어 수집을 중단했습니다."),
    PAGE_STRUCTURE_CHANGED(HttpStatus.UNPROCESSABLE_ENTITY, "페이지 구조가 바뀌어 읽을 수 없습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
