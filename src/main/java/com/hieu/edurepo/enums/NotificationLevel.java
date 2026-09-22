package com.hieu.edurepo.enums;

/**
 * Mức độ ưu tiên / tính khẩn cấp của thông báo hệ thống gửi đến người dùng.
 */
public enum NotificationLevel {
    /** Thông báo thông thường (bình luận, thông tin cập nhật học tập) */
    NORMAL("Thông thường"),

    /** Thông báo quan trọng (kết quả phê duyệt học liệu, cảnh báo tài khoản) */
    IMPORTANT("Quan trọng"),

    /** Thông báo khẩn cấp (thay đổi quy chế đào tạo, bảo trì máy chủ đột xuất) */
    URGENT("Khẩn cấp");

    /** Nhãn hiển thị mức độ ưu tiên */
    private final String label;

    NotificationLevel(String label) {
        this.label = label;
    }

    /**
     * Lấy nhãn mô tả mức độ quan trọng.
     * @return Nhãn mức độ thông báo tiếng Việt
     */
    public String getLabel() {
        return label;
    }
}
