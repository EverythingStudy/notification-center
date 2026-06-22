package com.notification.model.enums;

/**
 * 消息状态枚举
 */
public enum MessageStatusEnum {
    NORMAL(0, "正常"),
    RECALL(1, "已撤回"),
    EXPIRE(2, "已过期");

    private final int code;
    private final String desc;

    MessageStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
