package com.hieu.edurepo.enums;

public enum DocumentStatus {
    DRAFT("Bản nháp"),
    SUBMITTED("Đã gửi duyệt"),
    UNDER_REVIEW("Đang kiểm duyệt"),
    REVISION_REQUIRED("Cần chỉnh sửa"),
    RESUBMITTED("Đã gửi lại"),
    APPROVED("Đã phê duyệt"),
    PUBLISHED("Đã công bố"),
    REJECTED("Đã từ chối"),
    ARCHIVED("Đã lưu trữ");

    private final String label;

    DocumentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
