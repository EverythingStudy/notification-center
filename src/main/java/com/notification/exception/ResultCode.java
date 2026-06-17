package com.notification.exception;

import lombok.Getter;

@Getter
public enum ResultCode {
    // HTTP-style status codes
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    CONFLICT(409, "conflict"),
    INTERNAL_ERROR(500, "internal server error"),

    // Business error codes (1001+)
    TEMPLATE_NOT_FOUND(1001, "template not found"),
    TEMPLATE_CONTENT_EMPTY(1002, "template content is empty"),
    USER_NOT_FOUND(1003, "user not found"),
    USER_PREFERENCE_NOT_FOUND(1004, "user preference not found"),
    CHANNEL_NOT_FOUND(1005, "channel not found"),
    CHANNEL_UNAVAILABLE(1006, "channel is unavailable"),
    INVALID_PARAMETER(1007, "invalid parameter"),
    RATE_LIMIT_EXCEEDED(1008, "rate limit exceeded"),
    DUPLICATE_RECORD(1009, "duplicate record");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
