package com.hieu.edurepo.enums;

/**
 * Trạng thái vòng đời của một tài liệu/học liệu trong hệ thống EduRepo.
 * <p>
 * Luồng trạng thái chuẩn:
 * DRAFT -> SUBMITTED -> UNDER_REVIEW -> (REVISION_REQUIRED -> RESUBMITTED) -> APPROVED -> PUBLISHED
 * (hoặc REJECTED / ARCHIVED).
 * </p>
 */
public enum DocumentStatus {
    /** Bản nháp của tác giả, chưa gửi phê duyệt và chỉ tác giả mới nhìn thấy */
    DRAFT("Bản nháp"),

    /** Tác giả đã nộp bài, đang chờ phân công hoặc tiếp nhận kiểm duyệt */
    SUBMITTED("Đã gửi duyệt"),

    /** Giảng viên / Reviewer đang trong quá trình chấm điểm theo thang Rubric */
    UNDER_REVIEW("Đang kiểm duyệt"),

    /** Giảng viên yêu cầu tác giả chỉnh sửa, bổ sung nội dung kèm góp ý */
    REVISION_REQUIRED("Cần chỉnh sửa"),

    /** Tác giả đã cập nhật phiên bản tài liệu mới và gửi lại để thẩm định */
    RESUBMITTED("Đã gửi lại"),

    /** Tài liệu đạt chuẩn Rubric và được giảng viên phê duyệt thành công */
    APPROVED("Đã phê duyệt"),

    /** Quản trị viên/hệ thống đã xuất bản công khai lên kho học liệu chung cho sinh viên tải */
    PUBLISHED("Đã công bố"),

    /** Tài liệu bị từ chối do vi phạm quy chế hoặc không đạt tiêu chuẩn tối thiểu */
    REJECTED("Đã từ chối"),

    /** Tài liệu cũ hoặc hết hiệu lực đã được đưa vào lưu trữ, không hiển thị tìm kiếm mặc định */
    ARCHIVED("Đã lưu trữ");

    /** Nhãn hiển thị bằng tiếng Việt trên giao diện người dùng */
    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }

    /**
     * Lấy nhãn tiếng Việt hiển thị trên giao diện người dùng.
     * @return Chuỗi nhãn thân thiện (ví dụ: "Đã công bố")
     */
    public String getLabel() {
        return label;
    }
}
