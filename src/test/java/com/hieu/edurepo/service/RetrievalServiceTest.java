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
}
