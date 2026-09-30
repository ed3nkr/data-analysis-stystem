package com.reviewsales.common;

public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Object data;

    public BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public BusinessException(ErrorCode code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public ErrorCode code() {
        return code;
    }

    public Object data() {
        return data;
    }
}
