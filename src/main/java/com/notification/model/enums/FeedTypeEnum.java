package com.notification.model.enums;

/**
 * Feed 类型枚举
 * 对应设计文档中定义的 6 种消息流
 */
public enum FeedTypeEnum {
    SYSTEM("system", "系统消息"),
    VIP("vip", "VIP消息"),
    MARKETING("marketing", "营销消息"),
    ORDER("order", "订单消息"),
    LOGISTICS("logistics", "物流消息"),
    RISK("risk", "风控消息");

    private final String code;
    private final String displayName;

    FeedTypeEnum(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static FeedTypeEnum fromCode(String code) {
        for (FeedTypeEnum f : values()) {
            if (f.code.equals(code)) return f;
        }
        return SYSTEM;
    }
}
