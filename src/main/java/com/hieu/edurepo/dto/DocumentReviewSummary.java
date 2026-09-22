package com.hieu.edurepo.dto;

/**
 * Bản ghi DTO chứa tổng hợp điểm số đánh giá phục vụ hiển thị trên thẻ học liệu.
 */
public record DocumentReviewSummary(double averageRating, long reviewCount) {
    public static DocumentReviewSummary empty() { return new DocumentReviewSummary(0, 0); }
}
