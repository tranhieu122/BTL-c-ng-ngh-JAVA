package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.OpenAiProperties;
import com.hieu.edurepo.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingServiceImpl(OpenAiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    public List<Double> embedText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<List<Double>> result = embedBatch(List.of(text));
        return result.isEmpty() ? List.of() : result.get(0);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        if (!properties.isConfigured()) {
            LOGGER.debug("OpenAI API key not configured. Skipping embedding generation.");
            return List.of();
        }

        List<List<Double>> allEmbeddings = new ArrayList<>();
        int batchSize = 32;

        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            List<List<Double>> batchResult = callOpenAiEmbeddingApi(batch);
            allEmbeddings.addAll(batchResult);
        }

        return allEmbeddings;
    }

    private List<List<Double>> callOpenAiEmbeddingApi(List<String> inputs) {
        try {
            String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/embeddings";

            Map<String, Object> body = new HashMap<>();
            body.put("model", properties.getEmbeddingModel());
            body.put("input", inputs);

            String requestJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode dataArray = root.path("data");
                if (dataArray.isArray()) {
                    List<List<Double>> vectors = new ArrayList<>();
                    for (JsonNode item : dataArray) {
                        JsonNode embeddingNode = item.path("embedding");
                        if (embeddingNode.isArray()) {
                            List<Double> vec = new ArrayList<>();
                            for (JsonNode val : embeddingNode) {
                                vec.add(val.asDouble());
                            }
                            vectors.add(vec);
                        }
                    }
                    return vectors;
                }
            } else {
                LOGGER.error("OpenAI Embedding API error: HTTP {} - {}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate embedding from OpenAI API: {}", e.getMessage());
        }

        return List.of();
    }
}
