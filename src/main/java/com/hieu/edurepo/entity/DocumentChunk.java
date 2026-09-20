package com.hieu.edurepo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Thực thể JPA ánh xạ bảng `document_chunks` trong cơ sở dữ liệu.
 * Đại diện cho một đoạn văn bản được cắt nhỏ từ tài liệu gốc, đi kèm vector embedding phục vụ tìm kiếm ngữ nghĩa RAG.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    /** Khóa chính tự tăng của chunk */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Tài liệu gốc mà đoạn văn bản này thuộc về */
    @ManyToOne(optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** Thứ tự đoạn trong tài liệu gốc (bắt đầu từ 0) */
    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    /** Nội dung văn bản thực tế của đoạn (lưu dưới dạng MEDIUMTEXT) */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    /** Chuỗi JSON lưu trữ mảng vector embedding số thực (tính từ OpenAI hoặc mô hình embedding) */
    @Column(name = "embedding_json", columnDefinition = "MEDIUMTEXT")
    private String embeddingJson;

    /** Ước lượng số token của đoạn văn bản */
    @Column(name = "token_count")
    private int tokenCount;

    /** Trang trong file PDF chứa đoạn này (nếu có) */
    @Column(name = "page_number")
    private Integer pageNumber;

    /** Tiêu đề chương/phần chứa đoạn văn bản này (nếu xác định được) */
    @Column(name = "section_title", length = 255)
    private String sectionTitle;

    /** Tiêu đề mục con chứa đoạn văn bản này */
    @Column(name = "subsection_title", length = 255)
    private String subsectionTitle;

    /** Trang bắt đầu của đoạn văn bản */
    @Column(name = "start_page")
    private Integer startPage;

    /** Trang kết thúc của đoạn văn bản */
    @Column(name = "end_page")
    private Integer endPage;

    /** Nguồn trích xuất: TEXT_LAYER, OCR, METADATA... */
    @Column(name = "source_type", length = 50)
    private String sourceType;

    /** Thời điểm đoạn được lập chỉ mục và lưu vào hệ thống */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public DocumentChunk() {
    }

    public DocumentChunk(Document document, int chunkIndex, String content, String embeddingJson, int tokenCount) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embeddingJson = embeddingJson;
        this.tokenCount = tokenCount;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Document getDocument() {
        return document;
    }

    public void setDocument(Document document) {
        this.document = document;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public void setTokenCount(int tokenCount) {
        this.tokenCount = tokenCount;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public String getSubsectionTitle() {
        return subsectionTitle;
    }

    public void setSubsectionTitle(String subsectionTitle) {
        this.subsectionTitle = subsectionTitle;
    }

    public Integer getStartPage() {
        return startPage;
    }

    public void setStartPage(Integer startPage) {
        this.startPage = startPage;
    }

    public Integer getEndPage() {
        return endPage;
    }

    public void setEndPage(Integer endPage) {
        this.endPage = endPage;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
