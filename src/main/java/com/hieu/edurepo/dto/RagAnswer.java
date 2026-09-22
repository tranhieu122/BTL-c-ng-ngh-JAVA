package com.hieu.edurepo.dto;

import java.util.List;

/**
 * DTO đóng gói câu trả lời hoàn chỉnh của AI kèm các nguồn trích dẫn giáo trình.
 */
public record RagAnswer(String answer, List<RagSource> sources, boolean foundInformation) {

    public static final String NOT_FOUND_MESSAGE =
            "Mình chưa tìm thấy tài liệu phù hợp trong EduRepo.";

    public static final String INSUFFICIENT_MESSAGE =
            "Mình tìm thấy tài liệu liên quan nhưng chưa đủ thông tin để trả lời chính xác câu hỏi này. Bạn có thể mở tài liệu bên dưới để xem chi tiết.";

    public static RagAnswer notFound() {
        return new RagAnswer(NOT_FOUND_MESSAGE, List.of(), false);
    }
}
