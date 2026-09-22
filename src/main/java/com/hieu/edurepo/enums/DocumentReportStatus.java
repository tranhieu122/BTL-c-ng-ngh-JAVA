package com.hieu.edurepo.enums;

/**
 * Trạng thái xử lý của phiếu phản ánh vi phạm tài liệu.
 */
public enum DocumentReportStatus {
    PENDING("Chờ xử lý"),
    UNDER_REVIEW("Đang xem xét"),
    RESOLVED("Đã xử lý"),
    IGNORED("Bỏ qua");

    private final String label;

    DocumentReportStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
