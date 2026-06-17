package com.notification.model.enums;

public enum SendStatusEnum {
    PENDING(0, "待发送"),
    SENDING(1, "发送中"),
    SUCCESS(2, "发送成功"),
    FAILED(3, "发送失败");

    private final int code;
    private final String description;

    SendStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }
}
