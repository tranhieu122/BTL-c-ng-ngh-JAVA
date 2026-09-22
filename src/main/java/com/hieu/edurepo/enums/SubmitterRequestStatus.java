package com.hieu.edurepo.enums;

/**
 * Trạng thái của đơn xin cấp quyền đóng góp học liệu (Submitter Request).
 */
public enum SubmitterRequestStatus {
    /** Đơn mới tạo, đang chờ Quản trị viên hoặc Giảng viên xét duyệt */
    PENDING("Đang chờ duyệt"),

    /** Đơn được chấp thuận, người dùng tự động được gán vai trò ROLE_SUBMITTER */
    APPROVED("Đã duyệt"),

    /** Đơn bị từ chối do không đủ minh chứng hoặc không đạt yêu cầu */
    REJECTED("Đã từ chối");

    /** Nhãn trạng thái hiển thị bằng tiếng Việt */
    private final String label;

    SubmitterRequestStatus(String label) {
        this.label = label;
    }

    /**
     * Lấy nhãn hiển thị tiếng Việt của trạng thái đơn.
     * @return Tên trạng thái đơn xin quyền tác giả
     */
    public String getLabel() {
        return label;
    }
}
