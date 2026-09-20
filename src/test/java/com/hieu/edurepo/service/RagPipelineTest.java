package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RagPipelineTest {

    // =========================================================================
    // DocumentChunker Tests
    // =========================================================================

    @Nested
    @DisplayName("DocumentChunker")
    class ChunkerTests {

        private final DocumentChunker chunker = new DocumentChunker();

        @Test
        @DisplayName("Chunk Vietnamese text preserving sentence boundaries")
        void testChunkVietnameseText() {
            String text = "Cơ sở dữ liệu là tập hợp dữ liệu có tổ chức. " +
                    "Hệ quản trị cơ sở dữ liệu (DBMS) giúp quản lý dữ liệu hiệu quả. " +
                    "SQL là ngôn ngữ truy vấn chuẩn.";

            List<String> chunks = chunker.chunkText(text, 80, 20);
            assertFalse(chunks.isEmpty(), "Should produce at least one chunk");
            for (String chunk : chunks) {
                assertFalse(chunk.isBlank(), "No chunk should be blank");
                assertTrue(chunk.length() <= 100,
                        "Chunk length should be roughly within chunkSize + some buffer");
            }
        }

        @Test
        @DisplayName("Single short text returns single chunk")
        void testShortTextSingleChunk() {
            String text = "Spring Boot là framework";
            List<String> chunks = chunker.chunkText(text, 600, 100);
            assertEquals(1, chunks.size());
            assertEquals("Spring Boot là framework", chunks.get(0));
        }

        @Test
        @DisplayName("Null and blank text returns empty")
        void testNullBlankText() {
            assertEquals(List.of(), chunker.chunkText(null, 600, 100));
            assertEquals(List.of(), chunker.chunkText("   ", 600, 100));
        }
    }

    // =========================================================================
    // ContextBuilder Tests
    // =========================================================================

    @Nested
    @DisplayName("ContextBuilder")
    class ContextBuilderTests {

        @Test
        @DisplayName("Build context with search results includes document info")
        void testBuildContextWithResults() {
            RagProperties props = new RagProperties();
            props.setMaxContextTokens(3000);
            ContextBuilder builder = new ContextBuilder(props);

            Document doc = new Document();
            doc.setId(42L);
            doc.setTitle("Giáo trình Cơ sở dữ liệu");

            DocumentChunk chunk = new DocumentChunk(doc, 0,
                    "Cơ sở dữ liệu quan hệ sử dụng bảng để lưu trữ dữ liệu.", null, 20);

            List<RagSearchResult> results = List.of(new RagSearchResult(doc, chunk, 0.87));
            ContextBuilder.BuiltContext ctx = builder.buildContext("CSDL là gì?", results);

            assertNotNull(ctx);
            assertFalse(ctx.systemPrompt().isBlank());
            assertTrue(ctx.userPrompt().contains("CSDL là gì?"), "User question included");
            assertTrue(ctx.userPrompt().contains("Cơ sở dữ liệu quan hệ"), "Chunk content included");
            assertFalse(ctx.sources().isEmpty(), "Sources should not be empty");

            RagSource source = ctx.sources().get(0);
            assertEquals(1, source.sourceId());
            assertEquals(42L, source.documentId());
            assertEquals("Giáo trình Cơ sở dữ liệu", source.title());
            assertTrue(source.snippet().contains("Cơ sở dữ liệu quan hệ"));
        }

        @Test
        @DisplayName("Build context with empty results returns not-found context")
        void testBuildContextEmpty() {
            RagProperties props = new RagProperties();
            ContextBuilder builder = new ContextBuilder(props);
            ContextBuilder.BuiltContext ctx = builder.buildContext("test question", List.of());

            assertNotNull(ctx);
            assertTrue(ctx.userPrompt().contains("Không tìm thấy"));
            assertTrue(ctx.sources().isEmpty());
        }
    }

    // =========================================================================
    // RetrievalService Cosine Similarity Tests
    // =========================================================================

    @Nested
    @DisplayName("RetrievalService - Cosine Similarity")
    class CosineTests {

        @Test
        @DisplayName("Identical vectors return similarity 1.0")
        void testIdenticalVectors() {
            assertEquals(1.0, RetrievalServiceImpl.cosineSimilarity(
                    List.of(1.0, 0.0, 0.0), List.of(1.0, 0.0, 0.0)), 0.0001);
        }

        @Test
        @DisplayName("Orthogonal vectors return similarity 0.0")
        void testOrthogonalVectors() {
            assertEquals(0.0, RetrievalServiceImpl.cosineSimilarity(
                    List.of(1.0, 0.0), List.of(0.0, 1.0)), 0.0001);
        }

        @Test
        @DisplayName("Opposite vectors return similarity -1.0")
        void testOppositeVectors() {
            assertEquals(-1.0, RetrievalServiceImpl.cosineSimilarity(
                    List.of(1.0, 0.0), List.of(-1.0, 0.0)), 0.0001);
        }

        @Test
        @DisplayName("Null or empty vectors return 0.0")
        void testNullVectors() {
            assertEquals(0.0, RetrievalServiceImpl.cosineSimilarity((List<Double>) null, List.of(1.0)));
            assertEquals(0.0, RetrievalServiceImpl.cosineSimilarity(List.of(), List.of(1.0)));
        }

        @Test
        @DisplayName("double[] overload works correctly")
        void testDoubleArrayOverload() {
            assertEquals(1.0, RetrievalServiceImpl.cosineSimilarity(
                    new double[]{1.0, 0.0}, new double[]{1.0, 0.0}), 0.0001);
            assertEquals(0.0, RetrievalServiceImpl.cosineSimilarity(
                    new double[]{1.0, 0.0}, new double[]{0.0, 1.0}), 0.0001);
        }
    }

    // =========================================================================
    // RetrievalService Integration (with mocks)
    // =========================================================================

    @Nested
    @DisplayName("RetrievalService - Retrieve")
    class RetrieveTests {

        @Test
        @DisplayName("Vector search returns only chunks above similarity threshold")
        void testVectorSearchFiltering() throws Exception {
            DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
            EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
            RagProperties props = new RagProperties();
            props.setTopK(3);
            props.setSimilarityThreshold(0.5);
            ObjectMapper mapper = new ObjectMapper();

            Document doc = new Document();
            doc.setId(1L);
            doc.setTitle("Spring Boot Guide");

            DocumentChunk highSim = new DocumentChunk(doc, 0, "Spring Boot content",
                    mapper.writeValueAsString(List.of(1.0, 0.0)), 10);
            DocumentChunk lowSim = new DocumentChunk(doc, 1, "Other content",
                    mapper.writeValueAsString(List.of(0.0, 1.0)), 10);

            when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(highSim, lowSim));
            when(mockEmbedding.embedText("Spring Boot")).thenReturn(List.of(1.0, 0.0));

            RetrievalServiceImpl retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
            var results = retrieval.retrieve("Spring Boot");

            assertEquals(1, results.size());
            assertEquals(1L, results.get(0).document().getId());
            assertEquals(1.0, results.get(0).similarity(), 0.001);
        }

        @Test
        @DisplayName("Falls back to keyword matching when no embedding available")
        void testKeywordFallback() {
            DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
            EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
            RagProperties props = new RagProperties();
            props.setTopK(5);
            props.setSimilarityThreshold(0.1);
            ObjectMapper mapper = new ObjectMapper();

            Document doc = new Document();
            doc.setId(2L);
            doc.setTitle("Giáo trình CSDL");
            doc.setKeywords("cơ sở dữ liệu, SQL");

            DocumentChunk chunk = new DocumentChunk(doc, 0,
                    "Cơ sở dữ liệu là hệ thống lưu trữ dữ liệu", null, 15);

            when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(chunk));
            when(mockEmbedding.embedText(anyString())).thenReturn(List.of()); // No embedding

            RetrievalServiceImpl retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
            var results = retrieval.retrieve("dữ liệu");

            assertFalse(results.isEmpty(), "Keyword fallback should find matching chunks");
            assertEquals(2L, results.get(0).document().getId());
        }

        @Test
        @DisplayName("Blank query returns empty list")
        void testBlankQuery() {
            DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
            EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
            RagProperties props = new RagProperties();
            ObjectMapper mapper = new ObjectMapper();

            RetrievalServiceImpl retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
            assertTrue(retrieval.retrieve("").isEmpty());
            assertTrue(retrieval.retrieve(null).isEmpty());
            assertTrue(retrieval.retrieve("   ").isEmpty());
        }

        @Test
        @DisplayName("Chunks with null document are skipped")
        void testNullDocumentChunksSkipped() {
            DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
            EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
            RagProperties props = new RagProperties();
            props.setSimilarityThreshold(0.1);
            ObjectMapper mapper = new ObjectMapper();

            DocumentChunk orphan = new DocumentChunk(null, 0, "orphan content", null, 5);
            when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(orphan));
            when(mockEmbedding.embedText(anyString())).thenReturn(List.of());

            RetrievalServiceImpl retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
            assertTrue(retrieval.retrieve("orphan").isEmpty(),
                    "Chunks with null document should be skipped");
        }
    }
}
