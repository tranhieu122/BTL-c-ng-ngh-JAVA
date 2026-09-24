package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.OpenAiProperties;
import com.hieu.edurepo.service.OpenAIService;
import com.hieu.edurepo.service.ToolExecutorService;
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
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    private List<String> getModelCandidates() {
        List<String> models = new ArrayList<>();
        String primary = properties.getModel();
        if (primary != null && !primary.isBlank()) {
            models.add(primary.trim());
        }
        String baseUrl = properties.getBaseUrl();
        if (baseUrl != null && baseUrl.contains("googleapis.com")) {
            for (String fallback : List.of("gemini-3.5-flash-lite", "gemini-3-flash-preview", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-flash-latest")) {
                if (!models.contains(fallback)) {
                    models.add(fallback);
                }
            }
        }
        return models;
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            LOGGER.warn("OpenAI API key is missing or blank. Cannot generate answer with model {}.", properties.getModel());
            return "";
        }

        List<String> models = getModelCandidates();
        String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

        for (String currentModel : models) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", currentModel);
                body.put("messages", messages);

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
                    LOGGER.warn("OpenAI Chat Completion API error (Model: {}): HTTP {} - {}. Trying fallback model...",
                            currentModel, response.statusCode(), response.body());
                    if (response.statusCode() == 503 || response.statusCode() == 429) {
                        try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Exception calling OpenAI Chat Completion API (Model: {}): {}. Trying fallback model...",
                        currentModel, e.getMessage());
            }
        }

        return "";
    }

    @Override
    public com.hieu.edurepo.dto.ToolChatResponse generateChatWithTools(String systemPrompt, String userPrompt, ToolExecutorService toolExecutor) {
        if (!properties.isConfigured()) {
            LOGGER.warn("OpenAI API key is missing or blank. Cannot execute tool-calling chat with model {}.", properties.getModel());
            return new com.hieu.edurepo.dto.ToolChatResponse("", List.of(), List.of(), List.of());
        }

        List<com.hieu.edurepo.dto.RagSource> accumulatedSources = new ArrayList<>();
        List<com.hieu.edurepo.entity.Document> accumulatedDocuments = new ArrayList<>();
        List<String> toolsUsed = new ArrayList<>();

        try {
            String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

            List<Object> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

            List<Map<String, Object>> toolDefinitions = (toolExecutor != null) ? toolExecutor.getToolDefinitions() : List.of();

            int maxTurns = 5;
            for (int turn = 0; turn < maxTurns; turn++) {
                Map<String, Object> body = new HashMap<>();
                body.put("model", properties.getModel());
                body.put("messages", messages);

                if (!toolDefinitions.isEmpty()) {
                    body.put("tools", toolDefinitions);
                    body.put("tool_choice", "auto");
                    if (properties.getModel() != null && (properties.getModel().contains("gpt-5")
                            || properties.getModel().startsWith("o1")
                            || properties.getModel().startsWith("o3"))) {
                        body.put("reasoning_effort", "none");
                    }
                }

                String requestJson = objectMapper.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    LOGGER.error("OpenAI Tool Calling API error (Model: {}): HTTP {} - {}",
                            properties.getModel(), response.statusCode(), response.body());
                    return new com.hieu.edurepo.dto.ToolChatResponse("", accumulatedSources, toolsUsed, accumulatedDocuments);
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode choices = root.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    LOGGER.warn("OpenAI response did not contain choices: {}", response.body());
                    break;
                }

                JsonNode messageNode = choices.get(0).path("message");
                JsonNode toolCallsNode = messageNode.path("tool_calls");

                if (toolCallsNode.isArray() && !toolCallsNode.isEmpty()) {
                    // LLM yêu cầu gọi một hoặc nhiều công cụ
                    messages.add(messageNode);

                    for (JsonNode toolCall : toolCallsNode) {
                        String callId = toolCall.path("id").asText();
                        String toolName = toolCall.path("function").path("name").asText();
                        String arguments = toolCall.path("function").path("arguments").asText();

                        LOGGER.info("OpenAI yêu cầu thực thi Tool '{}' (Call ID: {}) với tham số: {}", toolName, callId, arguments);
                        if (!toolsUsed.contains(toolName)) {
                            toolsUsed.add(toolName);
                        }

                        com.hieu.edurepo.dto.ToolExecutionResult toolResult = (toolExecutor != null)
                                ? toolExecutor.executeTool(toolName, arguments)
                                : new com.hieu.edurepo.dto.ToolExecutionResult("{\"error\": \"No ToolExecutorService configured\"}");

                        if (toolResult.sources() != null && !toolResult.sources().isEmpty()) {
                            accumulatedSources.addAll(toolResult.sources());
                        }

                        if (toolResult.documents() != null && !toolResult.documents().isEmpty()) {
                            for (com.hieu.edurepo.entity.Document doc : toolResult.documents()) {
                                if (accumulatedDocuments.stream().noneMatch(existing -> existing.getId().equals(doc.getId()))) {
                                    accumulatedDocuments.add(doc);
                                }
                            }
                        }

                        Map<String, Object> toolMessage = new HashMap<>();
                        toolMessage.put("role", "tool");
                        toolMessage.put("tool_call_id", callId);
                        toolMessage.put("name", toolName);
                        toolMessage.put("content", toolResult.toolResultJson());
                        messages.add(toolMessage);
                    }
                    // Tiếp tục vòng lặp gửi tool message phản hồi lại cho OpenAI để nhận câu trả lời tổng hợp cuối cùng
                } else {
                    // Không còn tool call nào nữa, LLM đã trả về câu trả lời hoàn chỉnh
                    String finalContent = messageNode.path("content").asText("");
                    return new com.hieu.edurepo.dto.ToolChatResponse(finalContent.trim(), accumulatedSources, toolsUsed, accumulatedDocuments);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Lỗi ngoại lệ trong luồng OpenAI Tool Calling (Model: {}): {}", properties.getModel(), e.getMessage(), e);
        }

        return new com.hieu.edurepo.dto.ToolChatResponse("", accumulatedSources, toolsUsed, accumulatedDocuments);
    }

    @Override
    public void streamChatCompletion(String systemPrompt, String userPrompt,
                                     java.util.function.Consumer<String> tokenConsumer,
                                     Runnable onComplete,
                                     java.util.function.Consumer<Throwable> onError) {
        if (!properties.isConfigured()) {
            LOGGER.warn("OpenAI API key is missing or blank. Cannot stream answer with model {}.", properties.getModel());
            if (onError != null) onError.accept(new IllegalStateException("OpenAI API key is not configured"));
            return;
        }

        List<String> models = getModelCandidates();
        String endpoint = properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";

        List<Map<String, String>> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }
        messages.add(Map.of("role", "user", "content", userPrompt != null ? userPrompt : ""));

        Throwable lastError = null;

        for (String currentModel : models) {
            try {
                Map<String, Object> body = new HashMap<>();
                body.put("model", currentModel);
                body.put("messages", messages);
                body.put("stream", true);

                String requestJson = objectMapper.writeValueAsString(body);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + properties.getApiKey())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<java.io.InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(response.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith(":")) {
                                continue;
                            }
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) {
                                    break;
                                }
                                try {
                                    JsonNode root = objectMapper.readTree(data);
                                    JsonNode choices = root.path("choices");
                                    if (choices.isArray() && !choices.isEmpty()) {
                                        JsonNode delta = choices.get(0).path("delta");
                                        if (delta.has("content")) {
                                            String content = delta.path("content").asText("");
                                            if (!content.isEmpty() && tokenConsumer != null) {
                                                tokenConsumer.accept(content);
                                            }
                                        }
                                    }
                                } catch (Exception parseEx) {
                                    LOGGER.debug("Non-critical JSON chunk parse warning: {}", parseEx.getMessage());
                                }
                            }
                        }
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                    return;
                } else {
                    String errorBody = "";
                    try {
                        errorBody = new String(response.body().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                    LOGGER.warn("OpenAI Chat Completion Stream API error (Model: {}): HTTP {} - {}. Trying fallback model...",
                            currentModel, response.statusCode(), errorBody);
                    lastError = new RuntimeException("OpenAI Streaming HTTP " + response.statusCode() + " (" + currentModel + "): " + errorBody);
                    if (response.statusCode() == 503 || response.statusCode() == 429) {
                        try { Thread.sleep(400); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Exception calling OpenAI Chat Completion Stream API (Model: {}): {}. Trying fallback model...",
                        currentModel, e.getMessage());
                lastError = e;
            }
        }

        if (onError != null && lastError != null) {
            onError.accept(lastError);
        }
    }
}

