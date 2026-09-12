package com.hieu.edurepo.enums;

public enum DocumentReportStatus {
    PENDING("Chờ xử lý"),
    UNDER_REVIEW("Đang xem xét"),
    RESOLVED("Đã xử lý"),
    IGNORED("Bỏ qua");

    private final String label;

    DocumentReportStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
