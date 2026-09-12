package com.hieu.edurepo.enums;

public enum AuditResult {
    SUCCESS("Thành công"),
    FAILURE("Thất bại");

    private final String label;

    AuditResult(String label) { this.label = label; }
    public String getLabel() { return label; }
}
