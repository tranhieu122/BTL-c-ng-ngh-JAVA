package com.hieu.edurepo.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.dto.ToolExecutionResult;
import com.hieu.edurepo.service.ContextBuilder;
import com.hieu.edurepo.service.RetrievalService;
import com.hieu.edurepo.service.TimeService;
import com.hieu.edurepo.service.ToolExecutorService;
import com.hieu.edurepo.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Điều phối thực thi các Tool:
 * 1. search_documents: Gọi RAG RetrievalService hiện có của EduRepo, đóng gói context qua ContextBuilder, giữ nguyên RagSource.
 * 2. get_weather: Gọi WeatherService lấy thời tiết bên ngoài.
 * 3. get_current_time: Gọi TimeService lấy ngày giờ hệ thống.
 */
@Service
public class ToolExecutorServiceImpl implements ToolExecutorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolExecutorServiceImpl.class);

    private final RetrievalService retrievalService;
    private final ContextBuilder contextBuilder;
    private final WeatherService weatherService;
    private final TimeService timeService;
    private final ObjectMapper objectMapper;

    public ToolExecutorServiceImpl(RetrievalService retrievalService,
                                  ContextBuilder contextBuilder,
                                  WeatherService weatherService,
                                  TimeService timeService,
                                  ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.weatherService = weatherService;
        this.timeService = timeService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Map<String, Object>> getToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        // Tool 1: search_documents
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "search_documents",
                        "description", "Tìm kiếm tài liệu học tập, giáo trình, bài giảng và các đoạn trích nội dung (chunks) trong kho học liệu nội sinh EduRepo.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of(
                                                "type", "string",
                                                "description", "Từ khóa hoặc câu hỏi cần tra cứu tài liệu trong EduRepo"
                                        )
                                ),
                                "required", List.of("query")
                        )
                )
        ));

        // Tool 2: get_weather
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_weather",
                        "description", "Lấy thông tin thời tiết hiện tại (nhiệt độ, tình trạng mây mưa, độ ẩm, sức gió) cho một thành phố cụ thể.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "city", Map.of(
                                                "type", "string",
                                                "description", "Tên thành phố cần xem thời tiết, ví dụ: 'Hanoi', 'Ho Chi Minh City', 'Da Nang'"
                                        )
                                ),
                                "required", List.of("city")
                        )
                )
        ));

        // Tool 3: get_current_time
        tools.add(Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "get_current_time",
                        "description", "Lấy ngày và giờ hiện tại theo múi giờ chỉ định.",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "timezone", Map.of(
                                                "type", "string",
                                                "description", "Múi giờ IANA, ví dụ: 'Asia/Ho_Chi_Minh', 'UTC'. Mặc định: 'Asia/Ho_Chi_Minh'"
                                        )
                                ),
                                "required", List.of()
                        )
                )
        ));

        return tools;
    }

    @Override
    public ToolExecutionResult executeTool(String toolName, String argumentsJson) {
        if (toolName == null || toolName.isBlank()) {
            return new ToolExecutionResult("{\"error\": \"Tên công cụ không được để trống.\"}");
        }

        try {
            JsonNode argsNode = (argumentsJson != null && !argumentsJson.isBlank())
                    ? objectMapper.readTree(argumentsJson)
                    : objectMapper.createObjectNode();

            switch (toolName) {
                case "search_documents" -> {
                    String query = argsNode.path("query").asText("").trim();
                    if (query.isBlank()) {
                        return new ToolExecutionResult("{\"status\": \"EMPTY_QUERY\", \"message\": \"Câu hỏi tìm kiếm tài liệu trống.\"}");
                    }

                    LOGGER.info("Executing Tool 'search_documents' for query: {}", query);
                    List<RagSearchResult> searchResults = retrievalService != null ? retrievalService.retrieve(query) : List.of();
                    if (searchResults == null) searchResults = List.of();

                    ContextBuilder.BuiltContext builtContext = contextBuilder.buildContext(query, searchResults);

                    List<Map<String, Object>> sourcesSummary = new ArrayList<>();
                    for (RagSource src : builtContext.sources()) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("sourceId", src.sourceId());
                        item.put("documentId", src.documentId());
                        item.put("chunkId", src.chunkId());
                        item.put("title", src.title());
                        item.put("snippet", src.snippet());
                        item.put("detailUrl", src.detailUrl());
                        sourcesSummary.add(item);
                    }

                    Map<String, Object> resultPayload = new HashMap<>();
                    resultPayload.put("status", searchResults.isEmpty() ? "NO_DOCUMENTS_FOUND" : "SUCCESS");
                    resultPayload.put("document_count", searchResults.size());
                    resultPayload.put("context_prompt", builtContext.userPrompt());
                    resultPayload.put("sources", sourcesSummary);

                    List<com.hieu.edurepo.entity.Document> sourceDocuments = searchResults.stream()
                            .map(RagSearchResult::document)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

                    String json = objectMapper.writeValueAsString(resultPayload);
                    return new ToolExecutionResult(json, builtContext.sources(), sourceDocuments);
                }

                case "get_weather" -> {
                    String city = argsNode.path("city").asText("").trim();
                    if (city.isBlank()) {
                        city = "Hanoi"; // Mặc định Hà Nội nếu user không nêu rõ
                    }
                    LOGGER.info("Executing Tool 'get_weather' for city: {}", city);
                    String weatherResult = weatherService.getWeather(city);
                    return new ToolExecutionResult(weatherResult);
                }

                case "get_current_time" -> {
                    String timezone = argsNode.path("timezone").asText("Asia/Ho_Chi_Minh").trim();
                    if (timezone.isBlank()) timezone = "Asia/Ho_Chi_Minh";
                    LOGGER.info("Executing Tool 'get_current_time' for timezone: {}", timezone);
                    String timeResult = timeService.getCurrentTime(timezone);
                    return new ToolExecutionResult(timeResult);
                }

                default -> {
                    LOGGER.warn("Unknown tool requested: {}", toolName);
                    return new ToolExecutionResult(String.format("{\"error\": \"Công cụ '%s' không tồn tại trong hệ thống.\"}", toolName));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Lỗi khi thực thi tool '{}': {}", toolName, e.getMessage(), e);
            return new ToolExecutionResult(String.format("{\"status\": \"ERROR\", \"message\": \"Lỗi khi thực thi công cụ '%s': %s\"}", toolName, e.getMessage()));
        }
    }
}
