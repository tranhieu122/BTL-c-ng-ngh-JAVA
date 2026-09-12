package com.hieu.edurepo.dto;

public record DocumentReviewSummary(double averageRating, long reviewCount) {
    public static DocumentReviewSummary empty() { return new DocumentReviewSummary(0, 0); }
}
