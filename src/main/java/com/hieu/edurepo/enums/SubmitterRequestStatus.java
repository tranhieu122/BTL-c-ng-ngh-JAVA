package com.hieu.edurepo.enums;

public enum SubmitterRequestStatus {
    PENDING("Đang chờ duyệt"),
    APPROVED("Đã duyệt"),
    REJECTED("Đã từ chối");

    private final String label;

    SubmitterRequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
