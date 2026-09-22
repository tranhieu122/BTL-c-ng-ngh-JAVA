package com.hieu.edurepo.enums;

/**
 * Kết quả thực thi của hành vi kiểm toán (Thành công hoặc Thất bại).
 */
public enum AuditResult {
    SUCCESS("Thành công"),
    FAILURE("Thất bại");

    private final String label;

    AuditResult(String label) { this.label = label; }
    public String getLabel() { return label; }
}
