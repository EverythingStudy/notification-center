package com.notification.model.enums;

public enum PriorityEnum {
    HIGH("high"),
    NORMAL("normal"),
    LOW("low");

    private final String value;

    PriorityEnum(String value) { this.value = value; }

    public String getValue() { return value; }

    public static PriorityEnum fromValue(String value) {
        for (PriorityEnum p : values()) {
            if (p.value.equals(value)) return p;
        }
        return NORMAL;
    }
}
