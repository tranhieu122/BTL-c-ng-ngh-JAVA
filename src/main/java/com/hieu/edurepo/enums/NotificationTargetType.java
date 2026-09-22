package com.hieu.edurepo.enums;

/**
 * Đối tượng nhận thông báo hệ thống do Quản trị viên phát hành.
 */
public enum NotificationTargetType {
    /** Phát sóng thông báo cho tất cả thành viên trong toàn hệ thống */
    ALL("Tất cả người dùng"),

    /** Gửi thông báo đến nhóm người dùng theo vai trò cụ thể (ví dụ: tất cả REVIEWER) */
    ROLE("Người dùng theo vai trò"),

    /** Gửi thông báo đích danh tới một tài khoản người dùng duy nhất */
    USER("Một người dùng cụ thể");

    /** Nhãn hiển thị đối tượng đích */
    private final String label;

    NotificationTargetType(String label) {
        this.label = label;
    }

    /**
     * Lấy nhãn đối tượng nhận thông báo.
     * @return Tên đối tượng nhận thông báo tiếng Việt
     */
    public String getLabel() {
        return label;
    }
}
