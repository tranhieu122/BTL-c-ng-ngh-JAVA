package com.hieu.edurepo.dto;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng truyền tải dữ liệu (DTO) trả về từ Chatbot cho giao diện người dùng.
 * 
 * @param type Loại phản hồi: "RAG_ANSWER", "RESULTS", "GREETING", "OUT_OF_SCOPE", "INVALID_INPUT", "NO_RESULTS", "ERROR".
 * @param message Câu trả lời hoặc thông báo gửi tới người dùng (ánh xạ JsonProperty "answer" và alias "message").
 * @param query Nội dung truy vấn đã được chuẩn hóa.
 * @param filters Bộ lọc đang áp dụng (chủ đề, tác giả, năm...).
 * @param documents Danh sách tài liệu gợi ý dạng thẻ (khi tìm kiếm danh sách).
 * @param suggestions Danh sách nút câu hỏi gợi ý nhanh để người dùng bấm tiếp.
 * @param hasMore True nếu còn kết quả ở trang sau.
 * @param allResultsUrl Đường dẫn mở toàn bộ kết quả trên trang kho học liệu /repository.
 * @param context Trạng thái ngữ cảnh hội thoại hiện tại để lưu trữ giữa các lượt chat.
 * @param sources Danh sách các nguồn tham khảo (chunks) được AI dùng để trả lời (phục vụ trích dẫn [1], [2] và hover tooltip).
 */
public record DocumentAssistantResponse(String type, @JsonProperty("answer") String message, String query,
                                        Map<String, String> filters, List<DocumentAssistantItem> documents,
                                        List<String> suggestions, boolean hasMore,
                                        String allResultsUrl, DocumentAssistantContext context,
                                        List<RagSource> sources) {

    public DocumentAssistantResponse {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
        documents = documents == null ? List.of() : List.copyOf(documents);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        context = context == null ? DocumentAssistantContext.empty() : context;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public DocumentAssistantResponse(String type, String message, String query,
                                     Map<String, String> filters, List<DocumentAssistantItem> documents,
                                     List<String> suggestions, boolean hasMore,
                                     String allResultsUrl, DocumentAssistantContext context) {
        this(type, message, query, filters, documents, suggestions, hasMore, allResultsUrl, context, List.of());
    }

    public DocumentAssistantResponse(String type, String message, String query,
                                     List<DocumentAssistantItem> documents, boolean hasMore,
                                     String allResultsUrl) {
        this(type, message, query, Map.of(), documents, List.of(), hasMore, allResultsUrl,
                DocumentAssistantContext.empty(), List.of());
    }

    public static DocumentAssistantResponse message(String type, String message) {
        return message(type, message, List.of());
    }

    public static DocumentAssistantResponse message(String type, String message, List<String> suggestions) {
        return new DocumentAssistantResponse(type, message, "", Map.of(), List.of(), suggestions, false, null,
                DocumentAssistantContext.empty(), List.of());
    }

    /** Giữ tên message cho các consumer cũ; frontend mới dùng answer. */
    @JsonProperty("message")
    public String messageAlias() {
        return message;
    }
}
