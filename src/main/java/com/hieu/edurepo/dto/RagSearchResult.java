package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;

/**
 * DTO chứa kết quả truy xuất các đoạn văn bản tương đồng từ kho vector RAG.
 */
public record RagSearchResult(Document document, DocumentChunk chunk, double similarity) {
}
