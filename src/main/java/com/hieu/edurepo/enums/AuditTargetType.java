package com.hieu.edurepo.enums;

public enum AuditTargetType {
    PAGE("Trang"),
    USER("Người dùng"),
    DOCUMENT("Tài liệu"),
    BOOKMARK("Bookmark"),
    REVIEW("Kiểm duyệt"),
    CATEGORY("Danh mục"),
    FACULTY("Khoa"),
    DEPARTMENT("Bộ môn"),
    NOTIFICATION("Thông báo"),
    LOGIN("Đăng nhập"),
    SYSTEM("Hệ thống");

    private final String label;

    AuditTargetType(String label) { this.label = label; }
    public String getLabel() { return label; }
}
