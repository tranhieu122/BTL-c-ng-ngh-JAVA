package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.OpenAiProperties;
import com.hieu.edurepo.service.OpenAIService;
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
public class OpenAIServiceImpl implements OpenAIService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAIServiceImpl.class);

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAIServiceImpl(OpenAiProperties properties, ObjectMapper objectMapper) {
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
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            LOGGER.warn("OpenAI API key is missing or blank. Cannot generate answer with model {}.", properties.getModel());
            return "";
        }

        try {
            String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

            List<Map<String, String>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

            Map<String, Object> body = new HashMap<>();
            // Sử dụng model identifier chính thức được cấu hình (gpt-5.6-luna)
            body.put("model", properties.getModel());
            body.put("messages", messages);
            body.put("temperature", 0.2);

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
                JsonNode choices = root.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).path("message");
                    String content = messageNode.path("content").asText("");
                    return content.trim();
                }
            } else {
                LOGGER.error("OpenAI Chat Completion API error (Model: {}): HTTP {} - {}",
                        properties.getModel(), response.statusCode(), response.body());
            }
        } catch (Exception e) {
            LOGGER.error("Exception calling OpenAI Chat Completion API (Model: {}): {}",
                    properties.getModel(), e.getMessage());
        }

        return "";
    }
}
