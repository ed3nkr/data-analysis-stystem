package com.reviewsales.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 공통 응답 형식: 성공 {"success":true,"data":...}, 실패 {"success":false,"error":{...}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, null));
    }

    public static ApiResponse<Void> fail(String code, String message, Object data) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, data));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, Object data) {
    }
}
