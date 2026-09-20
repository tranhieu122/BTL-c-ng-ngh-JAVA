package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Document;

/**
 * Service interface điều phối quy trình đánh chỉ mục (indexing) tài liệu cho hệ thống RAG.
 *
 * <p>Khi một tài liệu được duyệt và công bố, hệ thống sẽ:</p>
 * <ol>
 *   <li>Trích xuất văn bản từ file (PDF/DOCX/...).</li>
 *   <li>Chia văn bản thành các đoạn nhỏ (chunks) bằng {@code DocumentChunker}.</li>
 *   <li>Tạo vector embedding cho từng chunk qua {@code EmbeddingService}.</li>
 *   <li>Lưu các chunk và embedding vào bảng {@code document_chunks}.</li>
 *   <li>Xóa cache chunk trong bộ nhớ để cập nhật chỉ mục RAG.</li>
 * </ol>
 *
 * <p>Implementation: {@code DocumentIndexingServiceImpl}.</p>
 */
public interface DocumentIndexingService {

    /**
     * Đánh chỉ mục tài liệu từ đối tượng {@link Document} đã có sẵn.
     * Thực hiện trích xuất, chunking, embedding và lưu vào CSDL.
     *
     * @param document Tài liệu cần đánh chỉ mục (phải có filePath hợp lệ).
     */
    void indexDocument(Document document);

    /**
     * Đánh chỉ mục tài liệu theo ID (tự tải tài liệu từ CSDL trước khi xử lý).
     *
     * @param documentId ID tài liệu cần đánh chỉ mục.
     */
    void indexDocument(Long documentId);

    /**
     * Xóa toàn bộ chunk và embedding của một tài liệu khỏi CSDL.
     * Gọi khi tài liệu bị xóa hoặc cần reindex từ đầu.
     *
     * @param documentId ID tài liệu cần xóa chỉ mục.
     */
    void removeIndex(Long documentId);

    /**
     * Reindex toàn bộ tài liệu đã công bố (PUBLISHED) trong hệ thống.
     * Dùng cho tác vụ bảo trì hoặc khi thay đổi mô hình embedding.
     *
     * @return Số lượng tài liệu đã được reindex thành công.
     */
    int reindexAllPublishedDocuments();
}
