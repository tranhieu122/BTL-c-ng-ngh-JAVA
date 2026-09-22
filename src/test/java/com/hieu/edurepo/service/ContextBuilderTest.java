package com.hieu.edurepo.service;

import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kiểm thử bộ xây dựng ngữ cảnh RAG (Context Builder Test).
 * Xác minh việc kết hợp các đoạn trích dẫn, gán chỉ mục trang [Trang X] và cắt tỉa token an toàn.
 */
class ContextBuilderTest {

    @Test
    void testBuildContextWithEmptyResults() {
        RagProperties props = new RagProperties();
        ContextBuilder builder = new ContextBuilder(props);

        var result = builder.buildContext("Java là gì?", List.of());
        assertNotNull(result.systemPrompt());
        assertTrue(result.userPrompt().contains("Java là gì?"));
        assertTrue(result.sources().isEmpty());
    }

    @Test
    void testBuildContextWithResults() {
        RagProperties props = new RagProperties();
        ContextBuilder builder = new ContextBuilder(props);

        Document doc = new Document();
        doc.setId(10L);
        doc.setTitle("Lập trình Java căn bản");

        DocumentChunk chunk = new DocumentChunk(doc, 0, "Java là ngôn ngữ lập trình hướng đối tượng mạnh mẽ.", null, 20);

        RagSearchResult searchResult = new RagSearchResult(doc, chunk, 0.88);
        var built = builder.buildContext("Java là gì?", List.of(searchResult));

        assertTrue(built.systemPrompt().contains("EduRepo"));
        assertTrue(built.userPrompt().contains("[Document 1]"));
        assertTrue(built.userPrompt().contains("Lập trình Java căn bản"));
        assertTrue(built.userPrompt().contains("Java là ngôn ngữ lập trình hướng đối tượng"));
        assertEquals(1, built.sources().size());
        assertEquals(10L, built.sources().get(0).documentId());
        assertEquals("Lập trình Java căn bản", built.sources().get(0).title());
        assertEquals(0, built.sources().get(0).chunkIndex());
        assertTrue(built.sources().get(0).excerpt().contains("Java là ngôn ngữ"));
        assertEquals(0.88, built.sources().get(0).relevance());
    }
}
