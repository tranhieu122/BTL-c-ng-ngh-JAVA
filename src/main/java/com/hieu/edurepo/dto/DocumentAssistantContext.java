package com.hieu.edurepo.dto;

/**
 * DTO lưu trữ ngữ cảnh tài liệu đang mở để phục vụ hội thoại hỏi đáp với Trợ lý AI.
 */
public record DocumentAssistantContext(String keyword, String topic, String author, String languageCode,
                                       Integer year, String sortMode, int page,
                                       String anchorAuthor, String anchorTopic) {

    public DocumentAssistantContext {
        keyword = clean(keyword);
        topic = clean(topic);
        author = clean(author);
        languageCode = clean(languageCode);
        sortMode = clean(sortMode);
        page = Math.max(0, page);
        anchorAuthor = clean(anchorAuthor);
        anchorTopic = clean(anchorTopic);
    }

    public static DocumentAssistantContext empty() {
        return new DocumentAssistantContext("", "", "", "", null, "", 0, "", "");
    }

    public boolean hasValues() {
        return !keyword.isEmpty() || !topic.isEmpty() || !author.isEmpty() || !languageCode.isEmpty()
                || year != null || !sortMode.isEmpty() || !anchorAuthor.isEmpty() || !anchorTopic.isEmpty();
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }
}
