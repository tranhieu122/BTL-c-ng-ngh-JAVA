package com.hieu.edurepo.enums;

public enum NotificationTargetType {
    ALL("Tất cả người dùng"),
    ROLE("Người dùng theo vai trò"),
    USER("Một người dùng cụ thể");

    private final String label;

    NotificationTargetType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
