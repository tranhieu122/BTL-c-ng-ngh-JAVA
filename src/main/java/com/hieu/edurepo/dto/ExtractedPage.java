package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.PageSourceType;

/**
 * Đại diện cho kết quả trích xuất văn bản có cấu trúc của một trang tài liệu (PDF).
 * Giữ nguyên thông tin số trang (pageNumber) và nguồn gốc (Text layer hay OCR scan).
 */
public class ExtractedPage {

    /** Số thứ tự trang trong tài liệu gốc (bắt đầu từ 1) */
    private final int pageNumber;

    /** Nội dung văn bản thô hoặc đã làm sạch của trang */
    private final String text;

    /** Nguồn trích xuất: TEXT_LAYER, OCR hoặc HYBRID */
    private final PageSourceType sourceType;

    /** Số ký tự thực tế có trên trang */
    private final int charCount;

    /** Đánh dấu trang này có phải là trang scan (ảnh/chữ quá ít) hay không */
    private final boolean scanned;

    public ExtractedPage(int pageNumber, String text, PageSourceType sourceType, boolean scanned) {
        this.pageNumber = pageNumber;
        this.text = text != null ? text : "";
        this.sourceType = sourceType != null ? sourceType : PageSourceType.TEXT_LAYER;
        this.charCount = this.text.strip().length();
        this.scanned = scanned;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getText() {
        return text;
    }

    public PageSourceType getSourceType() {
        return sourceType;
    }

    public int getCharCount() {
        return charCount;
    }

    public boolean isScanned() {
        return scanned;
    }

    @Override
    public String toString() {
        return "ExtractedPage{" +
                "page=" + pageNumber +
                ", source=" + sourceType +
                ", chars=" + charCount +
                ", scanned=" + scanned +
                '}';
    }
}
