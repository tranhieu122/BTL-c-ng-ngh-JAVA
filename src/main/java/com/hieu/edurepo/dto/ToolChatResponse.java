package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.Document;
import java.util.List;

/**
 * Kết quả trả về của một lượt xử lý Chat Completion có hỗ trợ Tool Calling.
 *
 * @param answer Câu trả lời văn bản cuối cùng của LLM gửi tới người dùng
 * @param sources Danh sách các nguồn trích dẫn được tích lũy từ các tool (đặc biệt là search_documents)
 * @param toolsUsed Danh sách tên các công cụ đã được AI quyết định gọi trong lượt hội thoại này
 * @param documents Danh sách Document entities được thu thập từ RAG search tool
 */
public record ToolChatResponse(String answer, List<RagSource> sources, List<String> toolsUsed, List<Document> documents) {

    public ToolChatResponse(String answer, List<RagSource> sources, List<String> toolsUsed) {
        this(answer, sources, toolsUsed, List.of());
    }

    public ToolChatResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
        toolsUsed = toolsUsed == null ? List.of() : List.copyOf(toolsUsed);
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
