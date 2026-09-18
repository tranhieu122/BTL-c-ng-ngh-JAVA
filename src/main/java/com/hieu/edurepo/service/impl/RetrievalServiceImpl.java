package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.RagProperties;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentChunk;
import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.EmbeddingService;
import com.hieu.edurepo.service.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public RetrievalServiceImpl(DocumentChunkRepository chunkRepository,
                                EmbeddingService embeddingService,
                                RagProperties ragProperties,
                                ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagSearchResult> retrieve(String query) {
        return retrieve(query, ragProperties.getTopK(), ragProperties.getSimilarityThreshold());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagSearchResult> retrieve(String query, int topK, double minSimilarity) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<DocumentChunk> publishedChunks = chunkRepository.findAllPublishedChunks();
        if (publishedChunks.isEmpty()) {
            LOGGER.debug("No published document chunks available in repository.");
            return List.of();
        }

        // 1. Thử Semantic Vector Search qua Embedding
        List<Double> queryEmbedding = embeddingService.embedText(query);
        if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
            List<RagSearchResult> vectorResults = new ArrayList<>();

            for (DocumentChunk chunk : publishedChunks) {
                List<Double> chunkVec = parseVector(chunk.getEmbeddingJson());
                if (chunkVec != null && !chunkVec.isEmpty()) {
                    double similarity = cosineSimilarity(queryEmbedding, chunkVec);
                    if (similarity >= minSimilarity) {
                        vectorResults.add(new RagSearchResult(chunk.getDocument(), chunk, similarity));
                    }
                }
            }

            if (!vectorResults.isEmpty()) {
                vectorResults.sort(Comparator.comparingDouble(RagSearchResult::similarity).reversed());
                return vectorResults.stream().limit(topK).toList();
            }
        }

        // 2. Fallback: Lexical Keyword & Semantic Hybrid Match khi chưa có vector embedding
        LOGGER.debug("Falling back to text similarity matching for query: {}", query);
        List<RagSearchResult> keywordResults = new ArrayList<>();
        Set<String> queryWords = extractWords(query);

        for (DocumentChunk chunk : publishedChunks) {
            double score = computeTextRelevance(queryWords, chunk);
            if (score >= Math.min(minSimilarity, 0.15)) {
                keywordResults.add(new RagSearchResult(chunk.getDocument(), chunk, score));
            }
        }

        keywordResults.sort(Comparator.comparingDouble(RagSearchResult::similarity).reversed());
        return keywordResults.stream().limit(topK).toList();
    }

    private List<Double> parseVector(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Double>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v1.size() != v2.size()) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.size(); i++) {
            double a = v1.get(i);
            double b = v2.get(i);
            dot += a * b;
            normA += a * a;
            normB += b * b;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double computeTextRelevance(Set<String> queryWords, DocumentChunk chunk) {
        if (queryWords.isEmpty() || chunk == null || chunk.getContent() == null) {
            return 0.0;
        }

        Document doc = chunk.getDocument();
        String fullText = (chunk.getContent() + " " +
                (doc != null && doc.getTitle() != null ? doc.getTitle() : "") + " " +
                (doc != null && doc.getKeywords() != null ? doc.getKeywords() : "")).toLowerCase(Locale.ROOT);

        long matches = queryWords.stream().filter(fullText::contains).count();
        if (matches == 0) {
            return 0.0;
        }

        return (double) matches / queryWords.size();
    }

    private Set<String> extractWords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }
}
