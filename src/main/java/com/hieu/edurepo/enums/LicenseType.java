package com.hieu.edurepo.enums;

/**
 * Giấy phép bản quyền áp dụng cho tài liệu khi công bố lên hệ thống.
 */
public enum LicenseType {
    /** Tác giả giữ toàn quyền sở hữu trí tuệ, cấm sao chép khi chưa xin phép */
    ALL_RIGHTS_RESERVED("Giữ toàn bộ bản quyền"),

    /** Giấy phép Creative Commons Ghi nhận tác giả (CC BY) */
    CC_BY("Creative Commons BY"),

    /** Giấy phép Creative Commons Ghi nhận tác giả - Chia sẻ tương tự (CC BY-SA) */
    CC_BY_SA("Creative Commons BY-SA"),

    /** Giấy phép Creative Commons Phi thương mại (CC BY-NC) */
    CC_BY_NC("Creative Commons BY-NC"),

    /** Tuyên bố từ bỏ bản quyền, tặng vào miền công cộng (CC0) */
    CC0("CC0 - Miền công cộng"),

    /** Tài liệu thuộc phạm vi công cộng, tự do sử dụng hoàn toàn */
    PUBLIC_DOMAIN("Miền công cộng"),

    /** Giấy phép đặc thù khác do tác giả quy định */
    OTHER("Giấy phép khác");

    /** Nhãn hiển thị tên giấy phép trên trang chi tiết tài liệu */
    private final String label;

    LicenseType(String label) {
        this.label = label;
    }

    /**
     * Lấy tên hiển thị của giấy phép bản quyền.
     * @return Tên giấy phép bản quyền
     */
    public String getLabel() {
        return label;
    }
}
