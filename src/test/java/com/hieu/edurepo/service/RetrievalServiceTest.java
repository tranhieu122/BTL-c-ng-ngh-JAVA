package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.impl.RetrievalServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Kiểm thử dịch vụ truy xuất tài liệu (Retrieval Service Test).
 * Đánh giá cơ chế tìm kiếm lai (Hybrid Search) kết hợp giữa Fulltext Search và Vector Search.
 */
class RetrievalServiceTest {

    @Test
    void testCosineSimilarityCalculation() {
        List<Double> v1 = List.of(1.0, 0.0, 0.0);
        List<Double> v2 = List.of(1.0, 0.0, 0.0);
        assertEquals(1.0, RetrievalServiceImpl.cosineSimilarity(v1, v2), 0.0001);

        List<Double> v3 = List.of(0.0, 1.0, 0.0);
        assertEquals(0.0, RetrievalServiceImpl.cosineSimilarity(v1, v3), 0.0001);

        List<Double> v4 = List.of(-1.0, 0.0, 0.0);
        assertEquals(-1.0, RetrievalServiceImpl.cosineSimilarity(v1, v4), 0.0001);
    }

    @Test
    void testRetrieveWithThreshold() throws Exception {
        DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
        EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
        RagProperties props = new RagProperties();
        props.setTopK(3);
        props.setSimilarityThreshold(0.5);
        ObjectMapper mapper = new ObjectMapper();

        Document doc = new Document();
        doc.setId(1L);
        doc.setTitle("Spring Boot Guide");

        DocumentChunk chunk1 = new DocumentChunk(doc, 0, "Spring Boot content", mapper.writeValueAsString(List.of(1.0, 0.0)), 10);
        DocumentChunk chunk2 = new DocumentChunk(doc, 1, "Other content", mapper.writeValueAsString(List.of(0.0, 1.0)), 10);

        when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(chunk1, chunk2));
        when(mockEmbedding.embedText("Spring Boot")).thenReturn(List.of(1.0, 0.0));

        RetrievalService retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
        var results = retrieval.retrieve("Spring Boot");

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).document().getId());
        assertEquals(1.0, results.get(0).similarity(), 0.001);
    }

    @Test
    void lexicalFallbackMatchesVietnameseWithoutDiacritics() {
        DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
        EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
        RagProperties props = new RagProperties();
        props.setSimilarityThreshold(0.9);
        ObjectMapper mapper = new ObjectMapper();

        Document doc = new Document();
        doc.setId(2L);
        doc.setTitle("Cơ sở dữ liệu");
        DocumentChunk chunk = new DocumentChunk(doc, 3,
                "Cơ sở dữ liệu quan hệ sử dụng bảng và khóa.", null, 12);

        when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(chunk));
        when(mockEmbedding.embedText("co so du lieu")).thenReturn(List.of());

        RetrievalService retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);
        var results = retrieval.retrieve("co so du lieu");

        assertEquals(1, results.size());
        assertEquals(3, results.get(0).chunk().getChunkIndex());
    }

    /**
     * KIỂM THỬ CHỐNG LỆCH CHỦ ĐỀ (ANTI-CONTAMINATION REGRESSION TEST):
     * Đảm bảo rằng khi người dùng đặt câu hỏi tự nhiên về "Spring Boot":
     * - Dù câu hỏi có nhiều từ dừng thông dụng ("Tìm tài liệu về", "giúp ích gì"...),
     * - Tài liệu "Nhập môn trí tuệ nhân tạo" (chứa nhiều từ dừng nhưng không có từ "spring" hay "boot")
     *   tuyệt đối KHÔNG ĐƯỢC PHÉP lọt vào danh sách kết quả!
     * - Chỉ tài liệu Spring Boot thực sự mới được trả về.
     */
    @Test
    void testAntiContaminationWhenQueryingSpringBootShouldNotMatchAiBook() {
        DocumentChunkRepository mockRepo = Mockito.mock(DocumentChunkRepository.class);
        EmbeddingService mockEmbedding = Mockito.mock(EmbeddingService.class);
        RagProperties props = new RagProperties();
        props.setTopK(3);
        props.setSimilarityThreshold(0.2);
        ObjectMapper mapper = new ObjectMapper();

        // Tài liệu 1: Sách AI "Nhập môn trí tuệ nhân tạo" (Chứa rất nhiều từ dừng thông dụng tiếng Việt)
        Document docAi = new Document();
        docAi.setId(1L);
        docAi.setTitle("Nhap_mon_Tri-tue-nhan-tao");
        DocumentChunk chunkAi = new DocumentChunk(docAi, 0,
                "Tìm kiếm trong không gian trạng thái giúp ích cho việc giải quyết các bài toán tối ưu về trí tuệ nhân tạo.",
                null, 20);

        // Tài liệu 2: Tài liệu chuẩn "Spring Boot Reference Documentation"
        Document docSpringBoot = new Document();
        docSpringBoot.setId(15L);
        docSpringBoot.setTitle("Tài liệu hướng dẫn và tham khảo chính thức Spring Boot");
        DocumentChunk chunkSpringBoot = new DocumentChunk(docSpringBoot, 0,
                "Spring Boot giúp đơn giản hóa việc khởi tạo và phát triển ứng dụng Spring độc lập.",
                null, 15);

        when(mockRepo.findAllPublishedChunks()).thenReturn(List.of(chunkAi, chunkSpringBoot));
        // Giả lập embedding API trả về rỗng để kích hoạt luồng Lexical Keyword Matching
        when(mockEmbedding.embedText(Mockito.anyString())).thenReturn(List.of());

        RetrievalService retrieval = new RetrievalServiceImpl(mockRepo, mockEmbedding, props, mapper);

        // Truy vấn câu hỏi tự nhiên dài nhiều từ dừng
        var results = retrieval.retrieve("Tìm tài liệu về spring boot giúp ích gì");

        // Kiểm chứng:
        // 1. Kết quả phải trả về tài liệu Spring Boot
        assertFalse(results.isEmpty(), "Phải tìm thấy tài liệu Spring Boot");
        assertEquals(15L, results.get(0).document().getId(), "Tài liệu đứng đầu phải là Spring Boot");

        // 2. Tài liệu AI "Nhập môn trí tuệ nhân tạo" tuyệt đối không được xuất hiện trong kết quả
        boolean containsAiDoc = results.stream().anyMatch(r -> r.document().getId() == 1L);
        assertFalse(containsAiDoc, "Tài liệu Nhập môn trí tuệ nhân tạo không được phép xuất hiện khi hỏi về Spring Boot");
    }
}

