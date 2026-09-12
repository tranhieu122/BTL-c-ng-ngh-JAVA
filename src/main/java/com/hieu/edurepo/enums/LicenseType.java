package com.hieu.edurepo.enums;

public enum LicenseType {
    ALL_RIGHTS_RESERVED("Giữ toàn bộ bản quyền"),
    CC_BY("Creative Commons BY"),
    CC_BY_SA("Creative Commons BY-SA"),
    CC_BY_NC("Creative Commons BY-NC"),
    CC0("CC0 - Miền công cộng"),
    PUBLIC_DOMAIN("Miền công cộng"),
    OTHER("Giấy phép khác");

    private final String label;

    LicenseType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
