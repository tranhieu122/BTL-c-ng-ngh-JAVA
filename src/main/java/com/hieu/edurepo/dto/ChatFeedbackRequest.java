package com.hieu.edurepo.dto;

import com.hieu.edurepo.enums.FeedbackRating;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO tiếp nhận đánh giá phản hồi (Thumbs Up / Down) từ giao diện người dùng.
 */
public record ChatFeedbackRequest(
        @NotBlank(message = "Mã tin nhắn không được để trống")
        String messageId,

        @NotNull(message = "Đánh giá không được để trống (UP hoặc DOWN)")
        FeedbackRating rating,

        String reason,
        String comment
) {
}
