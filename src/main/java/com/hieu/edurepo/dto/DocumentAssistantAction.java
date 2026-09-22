package com.hieu.edurepo.dto;

/**
 * DTO đại diện cho các hành động đề xuất của Trợ lý AI (gợi ý câu hỏi, tra cứu nguồn).
 */
public record DocumentAssistantAction(String label, String message) {

    public DocumentAssistantAction {
        label = clean(label);
        message = clean(message);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
