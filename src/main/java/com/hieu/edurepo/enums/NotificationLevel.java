package com.hieu.edurepo.enums;

public enum NotificationLevel {
    NORMAL("Thông thường"),
    IMPORTANT("Quan trọng"),
    URGENT("Khẩn cấp");

    private final String label;

    NotificationLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
