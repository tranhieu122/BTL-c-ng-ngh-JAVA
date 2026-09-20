package com.hieu.edurepo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng đại diện cho một nguồn tài liệu tham khảo (Citation Source) trong phản hồi RAG.
 * Cung cấp thông tin nguồn để hiển thị trích dẫn [1], [2] và nội dung xem trước (hover tooltip).
 *
 * @param sourceId Số thứ tự của trích dẫn trong câu trả lời (1, 2, 3...) tương ứng với [1], [2]
 * @param documentId ID định danh của tài liệu trong hệ thống EduRepo
 * @param chunkId ID định danh của chunk cụ thể trong cơ sở dữ liệu (bảng document_chunks)
 * @param title Tên tài liệu được trích dẫn
 * @param relevance Điểm độ tương đồng ngữ nghĩa (Cosine similarity) giữa câu hỏi và đoạn trích dẫn
 * @param detailUrl Đường dẫn truy cập trang chi tiết tài liệu (/repository/{documentId})
 * @param excerpt Đoạn trích dẫn nội dung thực tế (khoảng 300-500 ký tự) từ chunk
 * @param chunkIndex Vị trí thứ tự của đoạn trong tài liệu gốc (bắt đầu từ 0)
 * @param pageNumber Trang trong file PDF chứa nội dung này (nếu xác định được)
 * @param sectionTitle Tiêu đề chương/phần chứa đoạn trích dẫn (nếu có)
 */
public record RagSource(
        Integer sourceId,
        Long documentId,
        Long chunkId,
        String title,
        Double relevance,
        String detailUrl,
        String excerpt,
        Integer chunkIndex,
        Integer pageNumber,
        String sectionTitle) {

    public RagSource(Integer sourceId, Long documentId, Long chunkId, String title,
                     Double relevance, String detailUrl, String excerpt, Integer chunkIndex, Integer pageNumber) {
        this(sourceId, documentId, chunkId, title, relevance, detailUrl, excerpt, chunkIndex, pageNumber, null);
    }

    public RagSource(Long documentId, String title, Double relevance, String detailUrl,
                     String excerpt, Integer chunkIndex, Integer pageNumber) {
        this(null, documentId, null, title, relevance, detailUrl, excerpt, chunkIndex, pageNumber, null);
    }

    public RagSource(Long documentId, String title, double relevance, String detailUrl) {
        this(null, documentId, null, title, relevance, detailUrl, "", null, null, null);
    }

    public RagSource {
        excerpt = excerpt == null ? "" : excerpt.strip();
    }

    @JsonProperty("snippet")
    public String snippet() {
        return excerpt;
    }
}
