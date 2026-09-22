package com.hieu.edurepo.enums;

/**
 * Loại sự kiện kích hoạt sinh ra thông báo hệ thống.
 */
public enum NotificationType {
    /** Tin nhắn thông báo trực tiếp từ Quản trị viên */
    ADMIN_MESSAGE,

    /** Tác giả vừa nộp một học liệu mới vào hàng đợi kiểm duyệt */
    DOCUMENT_SUBMITTED,

    /** Học liệu đã được tiếp nhận và bắt đầu kiểm duyệt */
    DOCUMENT_UNDER_REVIEW,

    /** Học liệu đã được giảng viên thẩm định và phê duyệt thành công */
    DOCUMENT_APPROVED,

    /** Học liệu bị giảng viên từ chối tiếp nhận */
    DOCUMENT_REJECTED,

    /** Giảng viên gửi yêu cầu tác giả chỉnh sửa, hoàn thiện lại học liệu */
    DOCUMENT_REVISION_REQUIRED,

    /** Học liệu đã chính thức được công bố trên kho tài nguyên chung */
    DOCUMENT_PUBLISHED
}
