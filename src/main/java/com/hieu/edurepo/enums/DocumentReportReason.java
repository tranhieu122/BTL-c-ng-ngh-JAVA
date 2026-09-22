package com.hieu.edurepo.enums;

/**
 * Danh sách các lý do báo cáo vi phạm nội dung học liệu từ người học.
 */
public enum DocumentReportReason {
    FILE_UNOPENABLE("File không mở được", true),
    WRONG_SUBJECT("Nội dung sai môn học/chủ đề", false),
    DUPLICATE("Tài liệu bị trùng", false),
    INAPPROPRIATE_CONTENT("Nội dung không phù hợp", true),
    COPYRIGHT_CONCERN("Có dấu hiệu vi phạm bản quyền", true),
    OTHER("Lý do khác", false);

    private final String label;
    private final boolean serious;

    DocumentReportReason(String label, boolean serious) {
        this.label = label;
        this.serious = serious;
    }

    public String getLabel() { return label; }
    public boolean isSerious() { return serious; }
}
