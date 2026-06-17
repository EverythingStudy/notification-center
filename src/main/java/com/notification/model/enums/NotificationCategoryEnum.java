package com.notification.model.enums;

public enum NotificationCategoryEnum {
    SYSTEM("system", "系统消息"),
    PAYMENT("payment", "支付消息"),
    MARKETING("marketing", "营销消息"),
    ACTIVITY("activity", "活动通知");

    private final String code;
    private final String displayName;

    NotificationCategoryEnum(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }

    public static NotificationCategoryEnum fromCode(String code) {
        for (NotificationCategoryEnum c : values()) {
            if (c.code.equals(code)) return c;
        }
        return SYSTEM;
    }
}
