package com.hieu.edurepo.dto;

/**
 * Bản ghi tóm tắt điểm đánh giá sao và tổng số lượt đánh giá của tài liệu.
 */
public record DocumentRatingSummary(Long documentId, double averageRating, long reviewCount) {
}
