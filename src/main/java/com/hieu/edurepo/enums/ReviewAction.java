package com.hieu.edurepo.enums;

/**
 * Các hành động thao tác trong quy trình thẩm định và phê duyệt học liệu.
 */
public enum ReviewAction {
    /** Tác giả nộp tài liệu lên ban biên tập */
    SUBMITTED,

    /** Giảng viên bấm bắt đầu thẩm định tài liệu */
    START_REVIEW,

    /** Giảng viên gửi phiếu yêu cầu tác giả chỉnh sửa lại tài liệu */
    REVISION_REQUESTED,

    /** Tác giả nộp lại tài liệu sau khi đã sửa đổi */
    RESUBMITTED,

    /** Giảng viên phê duyệt tài liệu đạt chuẩn Rubric */
    APPROVED,

    /** Giảng viên từ chối phê duyệt tài liệu */
    REJECTED,

    /** Đưa tài liệu đã duyệt lên kho học liệu công khai */
    PUBLISHED,

    /** Đưa tài liệu vào khu vực lưu trữ hết hạn */
    ARCHIVED
}
