package com.hieu.edurepo.enums;

/**
 * Nguồn gốc trích xuất nội dung văn bản của từng trang tài liệu trong Document Ingestion Pipeline.
 */
public enum PageSourceType {
    /** Trích xuất trực tiếp từ text layer của file PDF kỹ thuật số */
    TEXT_LAYER("Text Layer"),

    /** Nhận dạng quang học qua OCR đối với trang tài liệu dạng ảnh hoặc file PDF scan */
    OCR("OCR Engine"),

    /** Thông tin metadata (tiêu đề, tóm tắt, từ khóa, tác giả) bổ trợ */
    METADATA("Metadata"),

    /** Kết hợp cả text layer và OCR */
    HYBRID("Hybrid"),

    /** Không xác định */
    UNKNOWN("Unknown");

    private final String description;

    PageSourceType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
