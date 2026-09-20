package com.hieu.edurepo.dto;

/**
 * Đại diện cho một phần nội dung tài liệu gắn liền với cấu trúc chương/mục (Section/Subsection).
 */
public class DocumentSection {

    /** Tiêu đề chương/phần chính (ví dụ: "1. Giới thiệu tổng quan" hoặc "Chương 3: Spring Security") */
    private final String sectionTitle;

    /** Tiêu đề mục con (ví dụ: "1.1 Bối cảnh ra đời" hoặc "3.1 Authentication") */
    private final String subsectionTitle;

    /** Nội dung văn bản thuộc section này */
    private final String content;

    /** Trang bắt đầu của section */
    private final int startPage;

    /** Trang kết thúc của section */
    private final int endPage;

    public DocumentSection(String sectionTitle, String subsectionTitle, String content, int startPage, int endPage) {
        this.sectionTitle = sectionTitle != null ? sectionTitle.strip() : null;
        this.subsectionTitle = subsectionTitle != null ? subsectionTitle.strip() : null;
        this.content = content != null ? content : "";
        this.startPage = startPage;
        this.endPage = endPage;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getSubsectionTitle() {
        return subsectionTitle;
    }

    public String getContent() {
        return content;
    }

    public int getStartPage() {
        return startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    @Override
    public String toString() {
        return "DocumentSection{" +
                "section='" + sectionTitle + '\'' +
                ", subsection='" + subsectionTitle + '\'' +
                ", pages=" + startPage + "-" + endPage +
                ", length=" + content.length() +
                '}';
    }
}
