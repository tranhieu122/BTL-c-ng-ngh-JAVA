package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.Document;
import java.util.List;

/**
 * Kết quả thực thi của một Tool trong hệ thống.
 * Chứa chuỗi JSON trả về cho LLM (toolResultJson), danh sách RagSource và danh sách Document gốc (nếu tool là search_documents).
 *
 * @param toolResultJson Chuỗi JSON kết quả trả về cho LLM
 * @param sources Danh sách nguồn trích dẫn sinh ra từ tool (nếu có)
 * @param documents Danh sách thực thể Document tương ứng từ RAG
 */
public record ToolExecutionResult(String toolResultJson, List<RagSource> sources, List<Document> documents) {

    public ToolExecutionResult(String toolResultJson) {
        this(toolResultJson, List.of(), List.of());
    }

    public ToolExecutionResult(String toolResultJson, List<RagSource> sources) {
        this(toolResultJson, sources, List.of());
    }

    public ToolExecutionResult {
        sources = sources == null ? List.of() : sources.stream().filter(java.util.Objects::nonNull).toList();
        documents = documents == null ? List.of() : documents.stream().filter(java.util.Objects::nonNull).toList();
    }
}
