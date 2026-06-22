package com.notification.model.enums;

/**
 * 消息发送类型枚举
 * BROADCAST: 广播消息，采用读扩散，不生成 user_message
 * USER: 用户消息，采用写扩散，写入 user_message
 */
public enum MessageSendTypeEnum {
    BROADCAST(0, "广播消息"),
    USER(1, "用户消息");

    private final int code;
    private final String desc;

    MessageSendTypeEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
