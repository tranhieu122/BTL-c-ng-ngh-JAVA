package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử tính năng Scoped Document Retrieval (Priority 3).
 * Xác minh:
 * - Khi lọc theo documentId = A, chỉ trả về chunk của document A, tuyệt đối không lấy document B.
 * - Khi tài liệu không có chunk hoặc query rỗng, trả về danh sách rỗng an toàn.
 */
class ScopedDocumentRetrievalTest {

    private DocumentChunkRepository chunkRepository;
    private EmbeddingService embeddingService;
    private RagProperties ragProperties;
    private ObjectMapper objectMapper;
    private RetrievalServiceImpl retrievalService;

    private Document docA;
    private Document docB;

    @BeforeEach
    void setUp() {
        chunkRepository = mock(DocumentChunkRepository.class);
        embeddingService = mock(EmbeddingService.class);
        ragProperties = new RagProperties();
        objectMapper = new ObjectMapper();

        retrievalService = new RetrievalServiceImpl(chunkRepository, embeddingService, ragProperties, objectMapper);

        docA = new Document();
        docA.setId(100L);
        docA.setTitle("Giáo trình Cấu trúc dữ liệu và Giải thuật");

        docB = new Document();
        docB.setId(200L);
        docB.setTitle("Lập trình Web với Spring Boot");

        DocumentChunk chunkA1 = new DocumentChunk();
        chunkA1.setId(1L);
        chunkA1.setDocument(docA);
        chunkA1.setContent("Thuật toán QuickSort có độ phức tạp trung bình O(n log n).");
        chunkA1.setChunkIndex(0);

        DocumentChunk chunkB1 = new DocumentChunk();
        chunkB1.setId(2L);
        chunkB1.setDocument(docB);
        chunkB1.setContent("Spring Security cung cấp cơ chế xác thực và phân quyền mạnh mẽ.");
        chunkB1.setChunkIndex(0);

        when(chunkRepository.findAllPublishedChunks()).thenReturn(List.of(chunkA1, chunkB1));
    }

    @Test
    @DisplayName("Chỉ trả về chunk của documentId chỉ định, không rò rỉ document khác")
    void retrievesOnlyChunksBelongingToRequestedDocument() {
        List<RagSearchResult> resultsA = retrievalService.retrieveForDocument(100L, "QuickSort");

        assertNotNull(resultsA);
        assertTrue(resultsA.stream().allMatch(r -> r.document().getId().equals(100L)));
        assertTrue(resultsA.stream().noneMatch(r -> r.document().getId().equals(200L)));

        List<RagSearchResult> resultsB = retrievalService.retrieveForDocument(200L, "Spring Security");

        assertNotNull(resultsB);
        assertTrue(resultsB.stream().allMatch(r -> r.document().getId().equals(200L)));
        assertTrue(resultsB.stream().noneMatch(r -> r.document().getId().equals(100L)));
    }

    @Test
    @DisplayName("Trả về danh sách rỗng khi documentId không tồn tại trong kho chunk")
    void returnsEmptyWhenDocumentHasNoChunks() {
        List<RagSearchResult> results = retrievalService.retrieveForDocument(999L, "QuickSort");
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Trả về danh sách rỗng khi query null hoặc blank")
    void returnsEmptyWhenQueryIsBlank() {
        assertTrue(retrievalService.retrieveForDocument(100L, null).isEmpty());
        assertTrue(retrievalService.retrieveForDocument(100L, "   ").isEmpty());
    }
}
