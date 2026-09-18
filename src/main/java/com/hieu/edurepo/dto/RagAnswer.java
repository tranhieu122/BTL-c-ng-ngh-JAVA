package com.hieu.edurepo.dto;

import java.util.List;

public record RagAnswer(String answer, List<RagSource> sources, boolean foundInformation) {

    public static final String NOT_FOUND_MESSAGE =
            "Xin lỗi, tôi không tìm thấy thông tin phù hợp trong kho học liệu EduRepo để trả lời câu hỏi này.";

    public static RagAnswer notFound() {
        return new RagAnswer(NOT_FOUND_MESSAGE, List.of(), false);
    }
}
