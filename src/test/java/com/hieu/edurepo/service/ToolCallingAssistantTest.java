package com.hieu.edurepo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.config.OpenAiProperties;
import com.hieu.edurepo.config.WeatherProperties;
import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.dto.RagSearchResult;
import com.hieu.edurepo.dto.RagSource;
import com.hieu.edurepo.dto.ToolChatResponse;
import com.hieu.edurepo.dto.ToolExecutionResult;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.repository.DocumentAssistantRepository;
import com.hieu.edurepo.service.impl.DocumentAssistantServiceImpl;
import com.hieu.edurepo.service.impl.TimeServiceImpl;
import com.hieu.edurepo.service.impl.ToolExecutorServiceImpl;
import com.hieu.edurepo.service.impl.WeatherServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ToolCallingAssistantTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("TimeService returns non-empty formatted time for Asia/Ho_Chi_Minh")
    void testTimeService() {
        TimeService timeService = new TimeServiceImpl();
        String result = timeService.getCurrentTime("Asia/Ho_Chi_Minh");

        assertNotNull(result);
        assertTrue(result.contains("Thời gian hiện tại") || result.contains("Giờ hiện tại") || result.contains(":"));
        assertTrue(result.contains("Asia/Ho_Chi_Minh"));
    }

    @Test
    @DisplayName("WeatherService returns fallback or valid weather information when API key is empty")
    void testWeatherServiceFallback() {
        WeatherProperties properties = new WeatherProperties();
        properties.setApiKey(""); // Không có API key
        WeatherService weatherService = new WeatherServiceImpl(properties, objectMapper);

        String result = weatherService.getWeather("Hanoi");
        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("Hà Nội") || result.contains("Hanoi") || result.contains("thời tiết") || result.contains("nhiệt độ"));
    }

    @Test
    @DisplayName("Test real OpenAI tool calling if key is present")
    void testOpenAiToolCallingDirect() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        org.junit.jupiter.api.Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                "Skip test: OPENAI_API_KEY is not configured in environment");

        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey.trim());
        properties.setModel("gpt-5.6-luna");

        com.hieu.edurepo.service.impl.OpenAIServiceImpl openAi = new com.hieu.edurepo.service.impl.OpenAIServiceImpl(properties, objectMapper);

        WeatherProperties weatherProperties = new WeatherProperties();
        WeatherService weatherService = new WeatherServiceImpl(weatherProperties, objectMapper);
        TimeService timeService = new TimeServiceImpl();

        ToolExecutorService executor = new ToolExecutorServiceImpl(null, null, weatherService, timeService, objectMapper);

        ToolChatResponse response = openAi.generateChatWithTools(
                "Bạn là trợ lý ảo EduRepo. Khi người dùng hỏi thời tiết hãy gọi tool get_weather.",
                "Thời tiết Hà Nội hôm nay thế nào?",
                executor
        );

        assertNotNull(response);
        assertFalse(response.answer().isBlank());
        assertTrue(response.toolsUsed().contains("get_weather"));
    }

    @Test
    @DisplayName("ToolExecutorService defines 3 tools: search_documents, get_weather, get_current_time")
    void testToolDefinitions() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);
        WeatherService weatherService = mock(WeatherService.class);
        TimeService timeService = mock(TimeService.class);

        ToolExecutorService executor = new ToolExecutorServiceImpl(
                retrievalService, contextBuilder, weatherService, timeService, objectMapper);

        List<Map<String, Object>> tools = executor.getToolDefinitions();
        assertEquals(3, tools.size());

        List<String> toolNames = tools.stream()
                .map(t -> (Map<String, Object>) t.get("function"))
                .map(f -> (String) f.get("name"))
                .toList();

        assertTrue(toolNames.contains("search_documents"));
        assertTrue(toolNames.contains("get_weather"));
        assertTrue(toolNames.contains("get_current_time"));
    }

    @Test
    @DisplayName("ToolExecutorService executes get_weather and get_current_time correctly")
    void testToolExecutionWeatherAndTime() {
        WeatherService weatherService = mock(WeatherService.class);
        when(weatherService.getWeather("Danang")).thenReturn("Thời tiết tại Danang: 28°C, Nắng nhẹ");

        TimeService timeService = mock(TimeService.class);
        when(timeService.getCurrentTime("Asia/Ho_Chi_Minh")).thenReturn("Giờ hiện tại: 10:30 (Asia/Ho_Chi_Minh)");

        ToolExecutorService executor = new ToolExecutorServiceImpl(
                null, null, weatherService, timeService, objectMapper);

        ToolExecutionResult weatherResult = executor.executeTool("get_weather", "{\"city\":\"Danang\"}");
        assertNotNull(weatherResult);
        assertEquals("Thời tiết tại Danang: 28°C, Nắng nhẹ", weatherResult.toolResultJson());

        ToolExecutionResult timeResult = executor.executeTool("get_current_time", "{\"timezone\":\"Asia/Ho_Chi_Minh\"}");
        assertNotNull(timeResult);
        assertEquals("Giờ hiện tại: 10:30 (Asia/Ho_Chi_Minh)", timeResult.toolResultJson());
    }

    @Test
    @DisplayName("ToolExecutorService executes search_documents and returns RagSources")
    void testToolExecutionSearchDocuments() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        ContextBuilder contextBuilder = mock(ContextBuilder.class);

        Document doc = new Document();
        doc.setId(99L);
        doc.setTitle("Giáo trình Spring Boot");

        RagSearchResult resultItem = new RagSearchResult(doc, null, 0.85);
        when(retrievalService.retrieve("Spring Boot")).thenReturn(List.of(resultItem));

        RagSource source = new RagSource(1, 99L, 101L, "Giáo trình Spring Boot", 0.85, "/repository/99", "Nội dung Spring Boot...", 0, 1);
        when(contextBuilder.buildContext(eq("Spring Boot"), any()))
                .thenReturn(new ContextBuilder.BuiltContext("System prompt", "User prompt", List.of(source)));

        ToolExecutorService executor = new ToolExecutorServiceImpl(
                retrievalService, contextBuilder, null, null, objectMapper);

        ToolExecutionResult toolResult = executor.executeTool("search_documents", "{\"query\":\"Spring Boot\"}");
        assertNotNull(toolResult);
        assertFalse(toolResult.sources().isEmpty());
        assertEquals(1, toolResult.sources().size());
        assertEquals(99L, toolResult.sources().get(0).documentId());
        assertFalse(toolResult.documents().isEmpty());
        assertEquals(99L, toolResult.documents().get(0).getId());
    }

    @Test
    @DisplayName("DocumentAssistantService routes weather query to Tool Calling")
    void testDocumentAssistantRoutesWeatherQuery() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        OpenAIService openAiService = mock(OpenAIService.class);
        ToolExecutorService toolExecutorService = mock(ToolExecutorService.class);

        when(openAiService.isAvailable()).thenReturn(true);
        when(openAiService.generateChatWithTools(anyString(), anyString(), any()))
                .thenReturn(new ToolChatResponse(
                        "Thời tiết Hà Nội hôm nay nhiều mây, nhiệt độ khoảng 26°C.",
                        List.of(),
                        List.of("get_weather"),
                        List.of()
                ));

        DocumentAssistantService assistant = new DocumentAssistantServiceImpl(
                repository, null, null, openAiService, null, null, toolExecutorService);

        DocumentAssistantResponse response = assistant.respond("Thời tiết Hà Nội hôm nay thế nào?");
        assertNotNull(response);
        assertEquals("ASSISTANT_ANSWER", response.type());
        assertTrue(response.message().contains("Thời tiết Hà Nội"));
        assertTrue(response.filters().containsKey("tools_used"));
        assertEquals("get_weather", response.filters().get("tools_used"));
    }

    @Test
    @DisplayName("DocumentAssistantService routes multi-tool query (weather + documents) with citations")
    void testDocumentAssistantMultiToolQueryWithCitations() {
        DocumentAssistantRepository repository = mock(DocumentAssistantRepository.class);
        OpenAIService openAiService = mock(OpenAIService.class);
        ToolExecutorService toolExecutorService = mock(ToolExecutorService.class);

        Document doc = new Document();
        doc.setId(10L);
        doc.setTitle("Kiến trúc Spring Security");

        RagSource source = new RagSource(1, 10L, 20L, "Kiến trúc Spring Security", 0.9, "/repository/10", "Spring Security hỗ trợ Authentication và Authorization", 0, 1);

        when(openAiService.isAvailable()).thenReturn(true);
        when(openAiService.generateChatWithTools(anyString(), anyString(), any()))
                .thenReturn(new ToolChatResponse(
                        "Thời tiết Hà Nội là 25°C. Về tài liệu, Spring Security là framework bảo mật cung cấp xác thực và phân quyền [1].",
                        List.of(source),
                        List.of("get_weather", "search_documents"),
                        List.of(doc)
                ));

        DocumentAssistantService assistant = new DocumentAssistantServiceImpl(
                repository, null, null, openAiService, null, null, toolExecutorService);

        DocumentAssistantResponse response = assistant.respond("Thời tiết Hà Nội hôm nay và tài liệu Spring Security");
        assertNotNull(response);
        assertEquals("RAG_ANSWER", response.type());
        assertTrue(response.message().contains("[1]"));
        assertFalse(response.sources().isEmpty());
        assertEquals(1, response.sources().size());
        assertEquals(10L, response.sources().get(0).documentId());
        assertFalse(response.documents().isEmpty());
        assertEquals(10L, response.documents().get(0).id());
    }
}
