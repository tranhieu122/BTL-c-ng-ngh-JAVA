package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.RagSearchResult;

import java.util.List;

/**
 * Interface định nghĩa dịch vụ truy xuất ngữ nghĩa (Retrieval Service) trong mô hình RAG.
 * Chịu trách nhiệm tìm kiếm các đoạn văn bản (chunks) phù hợp nhất với câu hỏi của người dùng.
 */
public interface RetrievalService {

    /**
     * Truy xuất các chunk tài liệu liên quan nhất theo câu hỏi người dùng (sử dụng cấu hình topK và ngưỡng mặc định).
     *
     * @param query Câu hỏi hoặc nội dung người dùng nhập vào
     * @return Danh sách kết quả tìm kiếm gồm tài liệu (Document), đoạn (DocumentChunk) và điểm tương đồng
     */
    List<RagSearchResult> retrieve(String query);

    /**
     * Truy xuất các chunk tài liệu liên quan nhất với tham số tùy chỉnh về số lượng và ngưỡng tương đồng.
     *
     * @param query Câu hỏi hoặc nội dung người dùng nhập vào
     * @param topK Số lượng kết quả tối đa cần lấy
     * @param minSimilarity Ngưỡng tương đồng tối thiểu (0.0 đến 1.0)
     * @return Danh sách kết quả tìm kiếm phù hợp
     */
    List<RagSearchResult> retrieve(String query, int topK, double minSimilarity);

    /**
     * Xóa bộ nhớ đệm (cache) chỉ mục chunk trong RAM khi có tài liệu mới được xuất bản hoặc reindex lại.
     */
    void invalidateChunkCache();
}
