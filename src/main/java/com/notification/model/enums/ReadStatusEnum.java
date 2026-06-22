package com.notification.model.enums;

/**
 * 用户消息已读状态枚举
 */
public enum ReadStatusEnum {
    UNREAD(0, "未读"),
    READ(1, "已读");

    private final int code;
    private final String desc;

    ReadStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
