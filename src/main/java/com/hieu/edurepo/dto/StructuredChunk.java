package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.PageSourceType;

/**
 * Đại diện cho một đoạn văn bản có cấu trúc đầy đủ metadata sau khi trải qua quá trình Semantic/Structure-aware Chunking.
 */
public class StructuredChunk {

    /** Thứ tự đoạn trong tài liệu gốc (bắt đầu từ 0) */
    private final int chunkIndex;

    /** Nội dung văn bản của đoạn */
    private final String content;

    /** Tiêu đề chương/phần chứa đoạn */
    private final String sectionTitle;

    /** Tiêu đề mục con chứa đoạn */
    private final String subsectionTitle;

    /** Trang bắt đầu của đoạn */
    private final Integer startPage;

    /** Trang kết thúc của đoạn */
    private final Integer endPage;

    /** Nguồn gốc dữ liệu của đoạn (TEXT_LAYER, OCR...) */
    private final PageSourceType sourceType;

    /** Ước lượng số lượng token */
    private final int tokenCount;

    public StructuredChunk(int chunkIndex,
                           String content,
                           String sectionTitle,
                           String subsectionTitle,
                           Integer startPage,
                           Integer endPage,
                           PageSourceType sourceType,
                           int tokenCount) {
        this.chunkIndex = chunkIndex;
        this.content = content != null ? content.strip() : "";
        this.sectionTitle = sectionTitle;
        this.subsectionTitle = subsectionTitle;
        this.startPage = startPage;
        this.endPage = endPage;
        this.sourceType = sourceType != null ? sourceType : PageSourceType.TEXT_LAYER;
        this.tokenCount = tokenCount > 0 ? tokenCount : Math.max(1, this.content.length() / 4);
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getSubsectionTitle() {
        return subsectionTitle;
    }

    public Integer getStartPage() {
        return startPage;
    }

    public Integer getEndPage() {
        return endPage;
    }

    public PageSourceType getSourceType() {
        return sourceType;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    /**
     * Tạo chuỗi ngữ cảnh bổ sung tiêu đề chương mục phục vụ tính embedding chính xác hơn
     */
    public String getContextualContent() {
        StringBuilder sb = new StringBuilder();
        if (sectionTitle != null && !sectionTitle.isBlank()) {
            sb.append(sectionTitle);
            if (subsectionTitle != null && !subsectionTitle.isBlank()) {
                sb.append(" > ").append(subsectionTitle);
            }
            sb.append("\n\n");
        }
        sb.append(content);
        return sb.toString();
    }
}
