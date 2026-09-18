package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;

public record RagSearchResult(Document document, DocumentChunk chunk, double similarity) {
}
