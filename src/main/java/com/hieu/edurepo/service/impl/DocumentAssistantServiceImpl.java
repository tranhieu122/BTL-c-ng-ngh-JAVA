package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.DocumentAssistantAction;
import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantItem;
import com.hieu.edurepo.dto.DocumentAssistantQuery;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.dto.DocumentRatingSummary;
import com.hieu.edurepo.repository.DocumentAssistantRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.dto.RagAnswer;
import com.hieu.edurepo.service.DocumentAssistantService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DocumentAssistantServiceImpl implements DocumentAssistantService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DocumentAssistantServiceImpl.class);

    private static final int FUZZY_CANDIDATE_LIMIT = 500;
    private static final String EMPTY_MESSAGE = "Bạn hãy nhập tên hoặc chủ đề tài liệu cần tìm.";
    private static final String NO_RESULTS_MESSAGE = "Mình chưa thấy kết quả thật khớp với \"%s\". Bạn có thể thử từ khóa ngắn hơn hoặc xem các tài liệu mới nhất.";
    private static final String GREETING_MESSAGE = "Xin chào! Mình có thể giúp bạn tìm học liệu đang có trên EduRepo.";
    private static final String OUT_OF_SCOPE_MESSAGE = "Mình chỉ hỗ trợ tra cứu học liệu trên EduRepo. Bạn hãy thử nhập tên, chủ đề, tác giả hoặc năm xuất bản.";
    private static final List<String> DEFAULT_SUGGESTIONS = List.of(
            "Tài liệu mới nhất", "Tài liệu nào được xem nhiều?", "Tìm tài liệu về cơ sở dữ liệu");

    private static final Pattern GREETING = Pattern.compile(
            "^(xin\\s+ch(?:à|a)o|ch(?:à|a)o(?:\\s+bạn)?|hello|hi|alo)[!,.?\\s]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LIST_ALL = Pattern.compile(
            "^(?:hiện|hien)?\\s*c(?:ó|o)\\s+(?:những|nhung)?\\s*(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang)(?:\\s+nào)?[!,.?\\s]*$|^"
                    + "(?:cho\\s+(?:tôi|toi|mình|minh)\\s+)?xem\\s+(?:tất cả|tat ca|danh sách|danh sach)\\s+(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang)[!,.?\\s]*$|^"
                    + "(?:tìm|tim)?\\s*(?:(?:tất cả|tat ca|danh sách|danh sach)\\s+)?(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang)(?:\\s+(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang))*\\s+(?:hiện có|hien co|đang có|dang co)[!,.?\\s]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OUT_OF_SCOPE = Pattern.compile(
            "\\b(tin tức|tin tuc|dịch sang|dich sang|viết (?:bài|code|mã)|viet (?:bai|code|ma)|làm bài|lam bai|giải bài|giai bai|tóm tắt|tom tat|soạn|soan|sáng tác|sang tac|kể chuyện|ke chuyen|nấu ăn|nau an)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NEWEST = Pattern.compile(
            "\\b(mới nhất|moi nhat|mới gần đây|moi gan day|gần đây|gan day|vừa đăng|vua dang|vừa xuất bản|vua xuat ban|tài liệu mới|tai lieu moi)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern POPULAR = Pattern.compile(
            "\\b(phổ biến|pho bien|xem nhiều|xem nhieu|được xem nhiều|duoc xem nhieu|tải nhiều|tai nhieu|được tải nhiều|duoc tai nhieu|nhiều lượt|nhieu luot|hot)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOP_RATED = Pattern.compile(
            "(?:^|\\s)(đánh giá cao|danh gia cao|rating cao|nhiều sao|nhieu sao|cao nhất về đánh giá|cao nhat ve danh gia)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOP_DOWNLOADED = Pattern.compile(
            "(?:^|\\s)(tải nhiều nhất|tai nhieu nhat|được tải nhiều nhất|duoc tai nhieu nhat|download nhiều|download nhieu)(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ENGLISH = Pattern.compile("\\b(tiếng\\s+anh|tieng\\s+anh|english|anh\\s+ngữ|anh\\s+ngu)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern VIETNAMESE = Pattern.compile("\\b(tiếng\\s+việt|tieng\\s+viet|vietnamese|việt\\s+ngữ|viet\\s+ngu)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern YEAR = Pattern.compile("\\b(?:năm|nam|xuất bản năm|xuat ban nam)?\\s*((?:19|20)\\d{2})\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern AUTHOR = Pattern.compile(
            "(?:^|\\s)(?:của|cua|tác giả|tac gia|do|người viết|nguoi viet|biên soạn bởi|bien soan boi)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TOPIC = Pattern.compile(
            "(?:^|\\s)(?:(?:thuộc|thuoc)\\s+)?(?:môn|mon|danh mục|danh muc|chủ đề|chu de|về|ve|liên quan đến|lien quan den)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LEADING_REQUEST = Pattern.compile(
            "^(?:(?:hãy|hay|vui lòng|vui long)\\s+)?(?:tìm(?:\\s+kiếm)?|tim(?:\\s+kiem)?|tra(?:\\s+cứu|\\s+cuu)?|cho\\s+(?:tôi|toi|mình|minh)\\s+xem|xem)"
                    + "(?:\\s+(?:cho\\s+(?:tôi|toi|mình|minh)))?(?:\\s+(?:các|cac|một|mot|những|nhung))?"
                    + "(?:\\s+(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang))?\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LEADING_HAVE = Pattern.compile(
            "^(?:edurepo\\s+)?c(?:ó|o)\\s+(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang)\\s*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern DOCUMENT_WORDS = Pattern.compile(
            "\\b(?:tài liệu|tai lieu|học liệu|hoc lieu|giáo trình|giao trinh|bài giảng|bai giang|bài học|bai hoc|sách|sach)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILLER_WORDS = Pattern.compile(
            "\\b(?:nào|nao|không|khong|có|cho|mình|minh|tôi|toi|xem|được|duoc|với|voi|nhé|nhe|ạ)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern TRAILING_POLITENESS = Pattern.compile(
            "(?:\\s+(?:không|khong|nào|nao|nhé|nhe|ạ|với|voi|thôi|thoi))?[!,.?\\s]*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final DocumentAssistantRepository repository;
    private final com.hieu.edurepo.service.RetrievalService retrievalService;
    private final com.hieu.edurepo.service.ContextBuilder contextBuilder;
    private final com.hieu.edurepo.service.OpenAIService openAiService;
    private final com.hieu.edurepo.config.RagProperties ragProperties;
    private final DocumentReviewRepository reviewRepository;
    private final com.hieu.edurepo.service.ToolExecutorService toolExecutorService;

    public DocumentAssistantServiceImpl(DocumentAssistantRepository repository) {
        this(repository, null, null, null, null, null, null);
    }

    public DocumentAssistantServiceImpl(DocumentAssistantRepository repository,
                                       com.hieu.edurepo.service.RetrievalService retrievalService,
                                       com.hieu.edurepo.service.ContextBuilder contextBuilder,
                                       com.hieu.edurepo.service.OpenAIService openAiService,
                                       com.hieu.edurepo.config.RagProperties ragProperties,
                                       DocumentReviewRepository reviewRepository) {
        this(repository, retrievalService, contextBuilder, openAiService, ragProperties, reviewRepository, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentAssistantServiceImpl(DocumentAssistantRepository repository,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.RetrievalService retrievalService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.ContextBuilder contextBuilder,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.OpenAIService openAiService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.config.RagProperties ragProperties,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) DocumentReviewRepository reviewRepository,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.ToolExecutorService toolExecutorService) {
        this.repository = repository;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.openAiService = openAiService;
        this.ragProperties = ragProperties;
        this.reviewRepository = reviewRepository;
        this.toolExecutorService = toolExecutorService;
    }

    @Override
    public DocumentAssistantResponse respond(String message) {
        return respond(message, DocumentAssistantContext.empty());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAssistantResponse respond(String message, DocumentAssistantContext suppliedContext) {
        return respond(message, suppliedContext, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAssistantResponse respond(String message, DocumentAssistantContext suppliedContext, Long scopedDocumentId) {
        DocumentAssistantContext context = suppliedContext == null ? DocumentAssistantContext.empty() : suppliedContext;
        String normalized = normalize(message);
        if (normalized.isEmpty()) {
            return messageWithContext("INVALID_INPUT", EMPTY_MESSAGE, List.of(), context);
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            return messageWithContext("INVALID_INPUT",
                    "Nội dung tìm kiếm không được vượt quá " + MAX_MESSAGE_LENGTH + " ký tự.", List.of(), context);
        }

        // =========================================================================
        // SCOPED PDF MODE (Tra cứu trực tiếp trong tài liệu đang mở)
        // =========================================================================
        if (scopedDocumentId != null) {
            Document scopedDoc = repository.findById(scopedDocumentId).orElse(null);
            if (scopedDoc == null || scopedDoc.getStatus() != com.hieu.edurepo.enums.DocumentStatus.PUBLISHED) {
                return messageWithContext("INVALID_INPUT", "Tài liệu này không tồn tại hoặc chưa được công bố.", List.of(), context);
            }

            if (retrievalService != null && contextBuilder != null && openAiService != null && openAiService.isAvailable()) {
                List<com.hieu.edurepo.dto.RagSearchResult> scopedResults = retrievalService.retrieveForDocument(scopedDocumentId, normalized);
                var builtContext = contextBuilder.buildScopedContext(normalized, scopedDocumentId, scopedDoc.getTitle(), scopedResults);
                String llmAnswer = openAiService.generateChatCompletion(builtContext.systemPrompt(), builtContext.userPrompt());
                String cleanedAnswer = stripAnswerStatus(llmAnswer);
                Map<String, String> meta = Map.of("model", "gpt-5.6-luna", "scope", "document", "documentId", String.valueOf(scopedDocumentId));
                List<DocumentAssistantItem> docs = List.of(toItem(scopedDoc));
                return new DocumentAssistantResponse(
                        "RAG_ANSWER",
                        cleanedAnswer != null && !cleanedAnswer.isBlank() ? cleanedAnswer : "Chưa có thông tin phù hợp trong tài liệu này.",
                        normalized,
                        meta,
                        docs,
                        List.of("Tóm tắt tài liệu này", "Chủ đề chính là gì?"),
                        false,
                        "/view/" + scopedDoc.getId(),
                        context,
                        builtContext.sources()
                );
            }
        }

        if (GREETING.matcher(normalized).matches()) {
            return messageWithContext("GREETING", GREETING_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }
        if (OUT_OF_SCOPE.matcher(normalized).find()) {
            return messageWithContext("OUT_OF_SCOPE", OUT_OF_SCOPE_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }

        DocumentAssistantQuery query = analyze(normalized, context);
        if (query.intent().equals("SEARCH") && query.keyword().isEmpty()
                && query.topic().isEmpty() && query.author().isEmpty()
                && query.languageCode().isEmpty() && query.year() == null) {
            return messageWithContext("INVALID_INPUT", EMPTY_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }


        // =========================================================================
        // Tool Calling AI Assistant (Weather, Time & Multi-tool Execution)
        // =========================================================================
        if (isToolCandidate(normalized) && toolExecutorService != null) {
            if (openAiService != null && openAiService.isAvailable()) {
                com.hieu.edurepo.dto.ToolChatResponse toolResponse = openAiService.generateChatWithTools(
                        buildToolAssistantSystemPrompt(), normalized, toolExecutorService);
                if (toolResponse != null && toolResponse.answer() != null && !toolResponse.answer().isBlank()) {
                    List<DocumentAssistantItem> docs = toolResponse.documents().stream().map(this::toItem).toList();
                    Map<String, String> meta = new HashMap<>();
                    meta.put("model", "gpt-5.6-luna");
                    if (!toolResponse.toolsUsed().isEmpty()) {
                        meta.put("tools_used", String.join(", ", toolResponse.toolsUsed()));
                    }
                    if (!toolResponse.sources().isEmpty()) {
                        meta.put("rag", "active");
                        return new DocumentAssistantResponse(
                                "RAG_ANSWER",
                                stripAnswerStatus(toolResponse.answer()),
                                normalized,
                                meta,
                                docs,
                                List.of("Tài liệu mới nhất", "Tìm tài liệu tương tự"),
                                false,
                                repositoryUrl(query),
                                context,
                                toolResponse.sources()
                        );
                    } else {
                        return new DocumentAssistantResponse(
                                "ASSISTANT_ANSWER",
                                toolResponse.answer(),
                                normalized,
                                meta,
                                List.of(),
                                DEFAULT_SUGGESTIONS,
                                false,
                                repositoryUrl(query),
                                context,
                                List.of()
                        );
                    }
                }
            }

            // Fallback khi OpenAI API chưa sẵn sàng hoặc gặp sự cố: gọi trực tiếp các tool và format câu trả lời thân thiện
            String folded = fold(normalized);
            StringBuilder fallbackAnswer = new StringBuilder();
            if (folded.contains("thoi tiet") || folded.contains("nhiet do") || folded.contains("mua") || folded.contains("nang")) {
                var res = toolExecutorService.executeTool("get_weather", "{\"city\":\"Hanoi\"}");
                fallbackAnswer.append(formatWeatherText(res.toolResultJson()));
            }
            if (folded.contains("may gio") || folded.contains("gio hien tai") || folded.contains("thoi gian")) {
                if (!fallbackAnswer.isEmpty()) fallbackAnswer.append("\n\n");
                var res = toolExecutorService.executeTool("get_current_time", "{}");
                fallbackAnswer.append(res.toolResultJson());
            }
            if (!fallbackAnswer.isEmpty()) {
                return new DocumentAssistantResponse(
                        "ASSISTANT_ANSWER",
                        fallbackAnswer.toString(),
                        normalized,
                        Map.of("fallback", "direct_tool"),
                        List.of(),
                        DEFAULT_SUGGESTIONS,
                        false,
                        repositoryUrl(query),
                        context,
                        List.of()
                );
            }
        }

        // =========================================================================
        // RAG (Retrieval-Augmented Generation) Pipeline with GPT-5.6 Luna
        // Đối với các lệnh cấu trúc (Tài liệu mới nhất, xem nhiều, cùng tác giả/danh mục):
        // Luôn ưu tiên tra cứu trực tiếp từ DB học liệu, không để RAG text-matching chiếm quyền.
        // =========================================================================
        if (isContentQuestion(normalized)
                && ragProperties != null && ragProperties.isEnabled() && retrievalService != null && contextBuilder != null) {
            List<com.hieu.edurepo.dto.RagSearchResult> ragResults = retrievalService.retrieve(normalized);
            // =========================================================================
            // MỞ RỘNG TRUY VẤN NGỮ NGHĨA KHI CÂU HỎI TỰ NHIÊN DÀI:
            // Nếu truy vấn nguyên văn (normalized) chưa tìm thấy chunks (do câu tự nhiên dài,
            // hoặc người dùng gõ ngắt quãng như "...bảo vệ phần"), hệ thống sẽ tự động thử tiếp
            // với topic hoặc keyword cốt lõi đã được trích xuất (ví dụ: "Spring Security").
            // =========================================================================
            if ((ragResults == null || ragResults.isEmpty()) && !query.topic().isBlank()) {
                ragResults = retrievalService.retrieve(query.topic());
            }
            if ((ragResults == null || ragResults.isEmpty()) && !query.keyword().isBlank()) {
                ragResults = retrievalService.retrieve(query.keyword());
            }
            if (ragResults == null) ragResults = List.of();

            var builtContext = contextBuilder.buildContext(normalized, ragResults);

            List<Document> sourceDocuments = ragResults.stream()
                    .map(com.hieu.edurepo.dto.RagSearchResult::document)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .toList();
            List<DocumentAssistantItem> documents = sourceDocuments.stream().map(this::toItem).toList();

            if (!ragResults.isEmpty() && openAiService != null && openAiService.isAvailable()) {
                String llmAnswer = openAiService.generateChatCompletion(builtContext.systemPrompt(), builtContext.userPrompt());
                if (llmAnswer != null && !llmAnswer.isBlank()) {
                    // =========================================================================
                    // BƯỚC XỬ LÝ PHẢN HỒI THÔNG MINH TỪ LLM (RAG ANSWER):
                    // 1. Gọt bỏ tiền tố STATUS: (nếu có) để lấy nội dung câu trả lời chuẩn.
                    // 2. Nếu AI phát hiện dữ liệu chỉ có một phần (Partial Coverage), hệ thống
                    //    GIỮ NGUYÊN câu trả lời phân tích sâu sắc của AI (kèm lưu ý phần chưa đủ)
                    //    thay vì xóa bỏ và thay bằng câu thông báo khô khan như trước đây.
                    // =========================================================================
                    String cleanedAnswer = stripAnswerStatus(llmAnswer);
                    // Kiểm tra nếu LLM gắn cờ INSUFFICIENT hoặc nội dung câu trả lời thừa nhận chưa có dữ liệu trong EduRepo
                    if (llmAnswer.startsWith("STATUS: INSUFFICIENT") || isInsufficientContent(cleanedAnswer)) {
                        // Nếu AI có nội dung giải thích (Partial Coverage), dùng nội dung đó;
                        // chỉ dùng INSUFFICIENT_MESSAGE mặc định khi chuỗi hoàn toàn rỗng.
                        String answerToShow = cleanedAnswer.isBlank() ? RagAnswer.INSUFFICIENT_MESSAGE : cleanedAnswer;
                        return new DocumentAssistantResponse("RAG_INSUFFICIENT", answerToShow,
                                normalized, java.util.Map.of("rag", "active"), documents,
                                List.of("Mở tài liệu nguồn"), false, repositoryUrl(query), context,
                                builtContext.sources());
                    }
                    return new DocumentAssistantResponse("RAG_ANSWER", cleanedAnswer, normalized,
                            java.util.Map.of("model", "gpt-5.6-luna", "rag", "active"),
                            documents, List.of("Tài liệu mới nhất", "Tìm tài liệu tương tự"),
                            false, repositoryUrl(query), context, builtContext.sources());
                }
            }

            if (!documents.isEmpty()) {
                // Nếu OpenAI chưa sẵn sàng: ưu tiên kiểm tra xem structured search có tài liệu khớp chính xác hơn không
                Page<Document> structuredPage = search(query);
                if (structuredPage != null && !structuredPage.isEmpty()) {
                    List<Document> structuredDocs = structuredPage.getContent();
                    List<DocumentAssistantItem> structuredItems = structuredDocs.stream().map(this::toItem).toList();
                    DocumentAssistantContext nextContext = contextFor(query, structuredDocs, context);
                    return new DocumentAssistantResponse("RESULTS", resultMessage(query, structuredPage.getTotalElements()),
                            queryText(query), query.filters(), structuredItems,
                            suggestionsFor(query, structuredDocs, structuredPage.hasNext()),
                            structuredPage.hasNext(), repositoryUrl(query), nextContext,
                            metadataSources(structuredDocs));
                }

                // Fallback khi OpenAI API chưa sẵn sàng hoặc gặp sự cố: trả kết quả Semantic Retrieval
                String fallbackMessage = "Dưới đây là các tài liệu trong EduRepo có nội dung liên quan phù hợp nhất với câu hỏi của bạn:";
                return new DocumentAssistantResponse("RESULTS", fallbackMessage, normalized,
                        java.util.Map.of("retrieval", "semantic"), documents,
                        suggestionsFor(query, sourceDocuments, false),
                        false, repositoryUrl(query), context, builtContext.sources());
            } else {
                // Nếu RAG không tìm thấy, kiểm tra tiếp xem structured search có tài liệu không
                Page<Document> structuredFallback = search(query);
                if (structuredFallback == null || structuredFallback.isEmpty()) {
                    // CHỐNG HALLUCINATION: Không tìm thấy tài liệu phù hợp trong kho học liệu EduRepo
                    return messageWithContext("NO_RESULTS", RagAnswer.NOT_FOUND_MESSAGE, DEFAULT_SUGGESTIONS, context);
                }
                List<Document> relatedDocuments = structuredFallback.getContent();
                return new DocumentAssistantResponse("RAG_INSUFFICIENT", RagAnswer.INSUFFICIENT_MESSAGE,
                        normalized, java.util.Map.of("rag", "active"),
                        relatedDocuments.stream().map(this::toItem).toList(),
                        List.of("Mở tài liệu nguồn"), false, repositoryUrl(query), context,
                        metadataSources(relatedDocuments));
            }
        }

        Page<Document> page = search(query);
        if (page.isEmpty()) {
            // =========================================================================
            // BƯỚC DỰ PHÒNG THÔNG MINH (SEMANTIC VECTOR RETRIEVAL FALLBACK):
            // Khi tìm kiếm có cấu trúc (SQL Exact/Fuzzy) không khớp được tài liệu nào
            // (thường xảy ra với các câu hỏi tự nhiên dài, nhiều ngữ cảnh),
            // hệ thống tận dụng RetrievalService (Semantic Search qua Vector Chunks)
            // để quét toàn bộ kho học liệu trước khi vội vã kết luận "Không tìm thấy".
            // =========================================================================
            if (retrievalService != null) {
                // Thử truy vấn bằng câu hỏi đầy đủ hoặc từ khóa trích xuất
                String semanticQuery = !normalized.isBlank() ? normalized
                        : (!query.keyword().isBlank() ? query.keyword() : query.topic());
                List<com.hieu.edurepo.dto.RagSearchResult> semanticResults = retrievalService.retrieve(semanticQuery);

                // Nếu câu dài không ra, thử tiếp với topic/keyword cốt lõi
                if ((semanticResults == null || semanticResults.isEmpty()) && !query.topic().isBlank()) {
                    semanticResults = retrievalService.retrieve(query.topic());
                }
                if ((semanticResults == null || semanticResults.isEmpty()) && !query.keyword().isBlank()) {
                    semanticResults = retrievalService.retrieve(query.keyword());
                }

                if (semanticResults != null && !semanticResults.isEmpty()) {
                    List<Document> semanticDocs = semanticResults.stream()
                            .map(com.hieu.edurepo.dto.RagSearchResult::document)
                            .filter(java.util.Objects::nonNull)
                            .distinct()
                            .toList();

                    if (!semanticDocs.isEmpty()) {
                        List<DocumentAssistantItem> items = semanticDocs.stream().map(this::toItem).toList();

                        // Nếu OpenAI LLM sẵn sàng: kích hoạt RAG để trả lời thông minh dựa trên chunks tìm được
                        if (openAiService != null && openAiService.isAvailable() && contextBuilder != null) {
                            var builtContext = contextBuilder.buildContext(normalized, semanticResults);
                            String llmAnswer = openAiService.generateChatCompletion(builtContext.systemPrompt(), builtContext.userPrompt());
                            if (llmAnswer != null && !llmAnswer.isBlank()) {
                                String cleanedAnswer = stripAnswerStatus(llmAnswer);
                                if (llmAnswer.startsWith("STATUS: INSUFFICIENT") || isInsufficientContent(cleanedAnswer)) {
                                    String answerToShow = cleanedAnswer.isBlank() ? RagAnswer.INSUFFICIENT_MESSAGE : cleanedAnswer;
                                    return new DocumentAssistantResponse("RAG_INSUFFICIENT", answerToShow,
                                            normalized, java.util.Map.of("rag", "active"), items,
                                            List.of("Mở tài liệu nguồn"), false, repositoryUrl(query), context,
                                            builtContext.sources());
                                }
                                return new DocumentAssistantResponse("RAG_ANSWER", cleanedAnswer, normalized,
                                        java.util.Map.of("model", "gpt-5.6-luna", "rag", "active"),
                                        items, List.of("Tài liệu mới nhất", "Tìm tài liệu tương tự"),
                                        false, repositoryUrl(query), context, builtContext.sources());
                            }
                        }

                        // Nếu LLM không sẵn sàng: trả về danh sách tài liệu tìm thấy qua Semantic Search
                        DocumentAssistantContext nextContext = contextFor(query, semanticDocs, context);
                        String fallbackMessage = "Dưới đây là các tài liệu trong EduRepo có nội dung liên quan phù hợp nhất với tìm kiếm của bạn:";
                        return new DocumentAssistantResponse("RESULTS", fallbackMessage,
                                queryText(query), query.filters(), items,
                                suggestionsFor(query, semanticDocs, false), false,
                                repositoryUrl(query), nextContext, metadataSources(semanticDocs));
                    }
                }
            }

            DocumentAssistantContext nextContext = contextFor(query, List.of(), context);
            return new DocumentAssistantResponse("NO_RESULTS", RagAnswer.NOT_FOUND_MESSAGE, queryText(query),
                    query.filters(), List.of(), suggestionsFor(query, List.of(), false), false,
                    repositoryUrl(query), nextContext);
        }

        List<Document> sourceDocuments = page.getContent();
        List<DocumentAssistantItem> documents = sourceDocuments.stream().map(this::toItem).toList();
        DocumentAssistantContext nextContext = contextFor(query, sourceDocuments, context);
        return new DocumentAssistantResponse("RESULTS", resultMessage(query, page.getTotalElements()),
                queryText(query), query.filters(), documents, suggestionsFor(query, sourceDocuments, page.hasNext()),
                page.hasNext(), repositoryUrl(query), nextContext, metadataSources(sourceDocuments));
    }

    private DocumentAssistantResponse messageWithContext(String type, String message, List<String> suggestions,
                                                         DocumentAssistantContext context) {
        return new DocumentAssistantResponse(type, message, "", java.util.Map.of(), List.of(), suggestions,
                false, null, context);
    }

    private DocumentAssistantQuery analyze(String message, DocumentAssistantContext context) {
        String folded = fold(message);
        if (isNewestFollowUp(folded) && context.hasValues()) {
            return fromContext(context, "LATEST", "newest", 0);
        }
        if (isPopularFollowUp(folded) && context.hasValues()) {
            return fromContext(context, "POPULAR", "popular", 0);
        }
        if (folded.matches("^(?:tim\\s+)?(?:tai lieu\\s+)?cung tac gia[!,.?\\s]*$") && context.hasValues()) {
            String author = firstNonBlank(context.anchorAuthor(), context.author());
            return new DocumentAssistantQuery("FILTERED_SEARCH", "", "", author, context.languageCode(),
                    context.year(), "relevant", 0);
        }
        if (folded.matches("^(?:tim\\s+)?(?:tai lieu\\s+)?cung (?:chu de|danh muc)[!,.?\\s]*$") && context.hasValues()) {
            String topic = firstNonBlank(context.anchorTopic(), context.topic(), context.keyword());
            return new DocumentAssistantQuery("FILTERED_SEARCH", "", topic, "", context.languageCode(),
                    context.year(), "relevant", 0);
        }
        if (folded.matches("^(?:xem\\s+)?(?:them|tiep|tat ca ket qua)[!,.?\\s]*$") && context.hasValues()) {
            int page = folded.contains("tat ca") ? 0 : context.page() + 1;
            return fromContext(context, intentFor(context.sortMode(), context), context.sortMode(), page);
        }

        boolean listAll = LIST_ALL.matcher(message).matches();
        String sortMode = TOP_RATED.matcher(message).find() ? "rating"
                : TOP_DOWNLOADED.matcher(message).find() ? "downloaded"
                : POPULAR.matcher(message).find() ? "popular"
                : NEWEST.matcher(message).find() || listAll ? "newest" : "relevant";
        String languageCode = extractLanguageCode(message);
        String working = sanitizeSearchText(message);
        if (!languageCode.isEmpty()) working = stripLanguageWords(working);
        Integer year = extractYear(working);
        if (year != null) working = YEAR.matcher(working).replaceAll(" ");

        String author = extract(AUTHOR, working);
        if (!author.isEmpty()) working = AUTHOR.matcher(working).replaceFirst(" ");

        String topic = extract(TOPIC, working);
        if (!topic.isEmpty()) working = TOPIC.matcher(working).replaceFirst(" ");

        String keyword = listAll ? "" : canonicalizeAlias(cleanupKeyword(working));
        topic = canonicalizeAlias(cleanupKeyword(topic));
        author = cleanupKeyword(author);
        if (!topic.isEmpty() && (keyword.isEmpty() || fold(keyword).equals(fold(topic)))) keyword = "";

        DocumentAssistantContext parsed = new DocumentAssistantContext(keyword, topic, author, languageCode,
                year, sortMode, 0, "", "");
        return new DocumentAssistantQuery(intentFor(sortMode, parsed), keyword, topic, author,
                languageCode, year, sortMode, 0);
    }

    private DocumentAssistantQuery fromContext(DocumentAssistantContext context, String intent,
                                               String sortMode, int page) {
        return new DocumentAssistantQuery(intent, context.keyword(), context.topic(), context.author(),
                context.languageCode(), context.year(), firstNonBlank(sortMode, "relevant"), page);
    }

    private String intentFor(String sortMode, DocumentAssistantContext context) {
        if ("rating".equals(sortMode)) return "TOP_RATED";
        if ("downloaded".equals(sortMode)) return "TOP_DOWNLOADED";
        if ("popular".equals(sortMode)) return "POPULAR";
        if ("newest".equals(sortMode)) return "LATEST";
        return context.author().isEmpty() && context.topic().isEmpty()
                && context.languageCode().isEmpty() && context.year() == null ? "SEARCH" : "FILTERED_SEARCH";
    }

    private boolean isNewestFollowUp(String folded) {
        return folded.matches("^(?:cai\\s+)?moi nhat(?:\\s+thoi)?[!,.?\\s]*$");
    }

    private boolean isPopularFollowUp(String folded) {
        return folded.matches("^(?:cai\\s+)?(?:pho bien|xem nhieu|tai nhieu)(?:\\s+thoi)?[!,.?\\s]*$");
    }

    private boolean isStructuredIntent(DocumentAssistantQuery query, String normalized) {
        if ("LATEST".equals(query.intent()) || "POPULAR".equals(query.intent())
                || "TOP_RATED".equals(query.intent()) || "TOP_DOWNLOADED".equals(query.intent())
                || "FILTERED_SEARCH".equals(query.intent())) {
            return true;
        }
        if (LIST_ALL.matcher(normalized).matches()) {
            return true;
        }
        String folded = fold(normalized);
        if (isNewestFollowUp(folded) || isPopularFollowUp(folded)) {
            return true;
        }
        if (folded.matches("^(?:tim\\s+)?(?:tai lieu\\s+)?cung (?:tac gia|chu de|danh muc)[!,.?\\s]*$")) {
            return true;
        }
        if (folded.matches("^(?:xem\\s+)?(?:them|tiep|tat ca ket qua)[!,.?\\s]*$")) {
            return true;
        }
        return false;
    }

    /**
     * =========================================================================================
     * KIỂM TRA Ý ĐỊNH HỌC THUẬT / NỘI DUNG (CONTENT QUESTION DETECTION):
     * Phương thức này quyết định câu người dùng nhập có được đưa vào pipeline RAG hay không.
     * 
     * LÝ DO NÂNG CẤP:
     * Trước đây, hệ thống có dòng kiểm tra:
     *   if (folded.matches(".*\\b(tim|tra cuu|cho toi xem|xem|tai lieu nao|tai lieu gi)\\b.*")) return false;
     * Dẫn đến khi người dùng đặt câu hỏi tự nhiên như:
     *   "Tìm tài liệu về spring security giúp ích gì trong việc bảo vệ phần"
     * thì do có từ "tìm" ở đầu câu, hệ thống lập tức loại trừ (trả về false), bỏ qua toàn bộ RAG
     * và chỉ tìm kiếm tiêu đề SQL thô, khiến EduBot báo "Mình chưa tìm thấy tài liệu phù hợp".
     *
     * CƠ CHẾ MỚI (3 BƯỚC THÔNG MINH):
     * 1. BƯỚC 1 (Ưu tiên học thuật): Nếu câu chứa các mẫu câu hỏi sâu về bản chất, công dụng, cơ chế,
     *    định nghĩa (ví dụ: "giúp ích gì", "là gì", "như thế nào", "tại sao", "bảo vệ... ra sao"),
     *    hệ thống LUÔN xác định đây là câu hỏi kiến thức (trả về true) để AI RAG đọc tài liệu trả lời.
     * 2. BƯỚC 2 (Phân loại danh sách): Nếu KHÔNG có từ hỏi học thuật ở Bước 1, mà chỉ có các từ
     *    yêu cầu liệt kê tài liệu (như "tìm tài liệu java", "tài liệu nào mới nhất"), hệ thống mới
     *    chuyển sang tìm kiếm metadata danh mục/tác giả (trả về false).
     * 3. BƯỚC 3: Dự phòng cho các câu hỏi thông thường hoặc câu kết thúc bằng dấu hỏi '?'.
     * =========================================================================================
     */
    private boolean isContentQuestion(String message) {
        String folded = fold(message);
        if (folded.isBlank()) return false;

        // BƯỚC 1: Khớp các mẫu câu hỏi học thuật / tìm hiểu kiến thức chuyên sâu
        boolean hasAcademicQuestionPattern = folded.matches(
                ".*\\b(giup ich gi|co ich gi|co loi ich gi|tac dung gi|vai tro gi|co y nghia gi|y nghia gi|muc dich gi|"
                + "la gi|la sao|dinh nghia|khai niem|the nao la|"
                + "nhu the nao|ra sao|hoat dong nhu the nao|hoat dong ra sao|van hanh the nao|"
                + "vi sao|tai sao|ly do gi|nguyen nhan gi|"
                + "giai thich|phan tich|so sanh|phan biet|khac nhau|giong nhau|"
                + "bao gom nhung gi|gom nhung gi|thanh phan nao|chuc nang gi|tinh nang gi|"
                + "cach nao|lam sao|lam the nao|huong dan|cac buoc|cach dung|cach su dung|cach cau hinh|cach cai dat|cach trien khai|"
                + "bao ve.*\\b(gi|nhu the nao|ra sao|phan nao|phan gi)|"
                + "co dung de|dung de lam gi)\\b.*"
        );
        if (hasAcademicQuestionPattern) {
            return true; // Người dùng đang hỏi kiến thức trong tài liệu -> chuyển vào luồng RAG
        }

        // BƯỚC 2: Khi KHÔNG có từ hỏi kiến thức, các câu chứa từ khóa tìm kiếm sẽ được xử lý dạng danh sách (SQL metadata)
        if (folded.matches(".*\\b(tim|tra cuu|cho toi xem|xem|tai lieu nao|tai lieu gi|danh sach)\\b.*")) {
            return false;
        }

        // BƯỚC 3: Các câu hỏi có từ 'cách', 'định nghĩa' hoặc kết thúc bằng dấu '?'
        return folded.matches(".*\\b(cach|dinh nghia)\\b.*") || folded.endsWith("?") || message.endsWith("?");
    }

    private boolean isToolCandidate(String message) {
        String folded = fold(message);
        boolean isWeather = folded.matches(".*\\b(thoi tiet|nhiet do|du bao thoi tiet|mua|nang|troi mua|troi nang|do am)\\b.*");
        boolean isTime = folded.matches(".*\\b(may gio|bay gio la|gio hien tai|thoi gian hien tai|ngay may|hom nay ngay|thu may|hom nay la thu|hom nay ngay bao nhieu)\\b.*");
        return isWeather || isTime;
    }

    /**
     * Xây dựng System Prompt cho luồng Agentic Tool Calling (đa công cụ: tra cứu tài liệu, thời tiết, giờ giấc).
     * Tích hợp các nguyên tắc Strict Grounding, chống ảo giác và xử lý partial coverage.
     */
    private String buildToolAssistantSystemPrompt() {
        return """
                # EDUREPO AI ASSISTANT — SYSTEM PROMPT (TOOL CALLING MODE)

                ## 1. VAI TRÒ (ROLE)
                Bạn là EduRepo AI Assistant - Trợ lý học thuật trí tuệ nhân tạo thuộc Hệ thống Quản lý Kho Học Liệu Nội Sinh EduRepo.
                Bạn được trang bị các công cụ (Tools) thực thi thời gian thực:
                1. `search_documents(query)`: Tra cứu giáo trình, tài liệu học tập, các đoạn trích nội dung (chunks) trong kho học liệu nội sinh EduRepo.
                2. `get_weather(city)`: Xem thông tin thời tiết hiện tại (nhiệt độ, thời tiết, độ ẩm, sức gió) tại một thành phố.
                3. `get_current_time(timezone)`: Xem ngày giờ hiện tại theo múi giờ IANA (mặc định 'Asia/Ho_Chi_Minh').

                ## 2. NGUYÊN TẮC HỌC THUẬT & STRICT GROUNDING (CHỐNG BỊA ĐẶT TUYỆT ĐỐI)
                - Khi người dùng hỏi về kiến thức, tài liệu, học phần, tác giả hoặc thông tin trong EduRepo:
                  + BẮT BUỘC gọi tool `search_documents` với từ khóa query súc tích, chuẩn xác nhất.
                  + MỌI thông tin factual BẮT BUỘC chỉ được rút ra từ kết quả do tool `search_documents` cung cấp. Tuyệt đối KHÔNG tự suy diễn, bịa đặt hoặc dùng kiến thức ngoài kho tài liệu.
                  + Nếu tool báo không tìm thấy tài liệu phù hợp: Trả lời lịch sự theo đúng nguyên tắc "Trong kho tài liệu EduRepo hiện chưa có dữ liệu giải đáp cho nội dung này."
                - QUY TẮC TRÍCH DẪN (CITATION): Khi trích dẫn thông tin từ tài liệu do tool trả về, bắt buộc ghi rõ nguồn bằng định dạng [1], [2] ở cuối câu/ý tương ứng với số thứ tự [Source 1], [Source 2] trong dữ liệu ngữ cảnh.
                - XỬ LÝ PARTIAL COVERAGE: Nếu tài liệu chỉ trả lời được một phần câu hỏi, hãy trả lời phần có dữ liệu (kèm citation [1], [2]), đồng thời nói rõ phần nào tài liệu chưa đề cập.

                ## 3. CÔNG CỤ THỜI TIẾT & THỜI GIAN
                - Khi hỏi thời tiết: Gọi `get_weather` (mặc định 'Hanoi' nếu không nêu thành phố).
                - Khi hỏi thời gian (mấy giờ, ngày mấy, thứ mấy): Gọi `get_current_time`.

                ## 4. HỖ TRỢ ĐA CÔNG CỤ (MULTI-TOOL)
                - Nếu câu hỏi kết hợp nhiều ý (ví dụ: vừa hỏi thời tiết vừa hỏi tài liệu học tập hoặc giờ giấc), bạn hãy gọi đồng thời tất cả các tool liên quan trong cùng một lượt để trả lời đầy đủ cho người dùng.

                ## 5. PHONG CÁCH PHẢN HỒI (STYLE)
                - Trả lời rõ ràng, sư phạm, thân thiện bằng tiếng Việt (hoặc theo ngôn ngữ người dùng).
                - Dùng Markdown (in đậm từ khóa, bullet points) để câu trả lời dễ đọc và mạch lạc.
                """;
    }

    private String formatWeatherText(String json) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if ("SUCCESS".equalsIgnoreCase(root.path("status").asText())) {
                String city = root.path("city").asText("Hà Nội");
                double temp = root.path("temperature_celsius").asDouble();
                String condition = root.path("condition").asText("");
                int humidity = root.path("humidity_percent").asInt();
                double wind = root.path("wind_kph").asDouble();
                return String.format("Thời tiết tại %s hiện tại: **%.1f°C**, %s. Độ ẩm khoảng **%d%%**, sức gió **%.1f km/h**.",
                        city, temp, condition, humidity, wind);
            }
        } catch (Exception ignored) {}
        return json;
    }

    private String stripAnswerStatus(String answer) {
        if (answer == null || answer.isBlank()) return "";
        String cleaned = answer.replaceFirst("^(?i)STATUS:\\s*(ANSWERED|INSUFFICIENT)\\s*", "").strip();

        // 1. Chuyển đổi "Theo Context [1]" thành "Theo tài liệu [1]" nếu có trích dẫn nguồn ngay sau
        cleaned = cleaned.replaceFirst("^(?i)(?:\\*\\*)?(?:theo|dựa\\s+(?:trên|vào))\\s+(?:retrieved\\s+)?context\\s*(\\[\\d+\\])", "Theo tài liệu $1");

        // 2. Gọt bỏ triệt để các cụm từ mở đầu máy móc như "Theo Context,", "Theo context:", "**Theo Context:**", "Dựa trên Context được cung cấp,"
        cleaned = cleaned.replaceFirst("^(?i)(?:\\*\\*)?(?:theo|dựa\\s+(?:trên|vào)|căn\\s+cứ\\s+(?:vào)?|trong)\\s+(?:retrieved\\s+)?context(?:\\s+được\\s+cung\\s+cấp)?[,:]?(?:\\*\\*)?[\\s,:—–-]+\\s*", "");

        // 3. Viết hoa lại chữ cái đầu nếu sau khi gọt bỏ bị viết thường
        if (!cleaned.isEmpty() && Character.isLowerCase(cleaned.charAt(0))) {
            cleaned = Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
        }

        // 4. Thay thế các từ "context" máy móc còn sót lại trong nội dung thành "tài liệu"
        cleaned = cleaned.replaceAll("(?i)\\btheo\\s+(?:retrieved\\s+)?context\\b", "theo tài liệu");
        cleaned = cleaned.replaceAll("(?i)\\btrong\\s+(?:retrieved\\s+)?context\\b", "trong tài liệu");
        cleaned = cleaned.replaceAll("(?i)\\btừ\\s+(?:retrieved\\s+)?context\\b", "từ tài liệu");
        cleaned = cleaned.replaceAll("(?i)\\bdựa\\s+trên\\s+(?:retrieved\\s+)?context\\b", "dựa trên tài liệu");

        return cleaned.strip();
    }

    /**
     * KIỂM TRA PHẢN HỒI THIẾU DỮ LIỆU TỪ AI (INSUFFICIENT CONTENT DETECTION):
     * Xác định xem câu trả lời của mô hình ngôn ngữ có thừa nhận rằng kho tài liệu EduRepo
     * không chứa thông tin hoặc chưa đủ dữ liệu để giải đáp hay không.
     * Nếu có, phản hồi sẽ được gán type "RAG_INSUFFICIENT" một cách nhất quán.
     */
    private boolean isInsufficientContent(String answer) {
        if (answer == null || answer.isBlank()) return true;
        String lower = answer.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("chưa có dữ liệu")
                || lower.contains("chua co du lieu")
                || lower.contains("chưa tìm thấy")
                || lower.contains("chua tim thay")
                || lower.contains("không có thông tin")
                || lower.contains("khong co thong tin")
                || lower.contains("chưa đủ thông tin")
                || lower.contains("chua du thong tin");
    }

    private Page<Document> search(DocumentAssistantQuery query) {
        Page<Document> exact = searchExact(query);
        if (!exact.isEmpty() || !hasTextFilter(query)) return exact;

        List<Document> candidates = repository.findPublishedCandidates(
                PageRequest.of(0, FUZZY_CANDIDATE_LIMIT, Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"))));
        if (candidates == null || candidates.isEmpty()) return exact;

        List<Document> matches = candidates.stream()
                .filter(document -> matches(document, query))
                .sorted(documentComparator(query))
                .toList();
        int start = Math.min(query.page() * MAX_RESULTS, matches.size());
        int end = Math.min(start + MAX_RESULTS, matches.size());
        return new PageImpl<>(matches.subList(start, end), PageRequest.of(query.page(), MAX_RESULTS), matches.size());
    }

    private Page<Document> searchExact(DocumentAssistantQuery query) {
        if (query.sortMode().equals("rating")) {
            return rankByRating(query);
        }
        if (query.sortMode().equals("popular") || query.sortMode().equals("downloaded")
                || query.sortMode().equals("newest")) {
            return repository.searchPublishedStructured(query.keyword(), query.topic(), query.author(),
                    query.languageCode(), query.year(), PageRequest.of(query.page(), MAX_RESULTS, sortFor(query)));
        }
        return repository.searchPublishedRelevant(query.keyword(), query.topic(), query.author(),
                query.languageCode(), query.year(), PageRequest.of(query.page(), MAX_RESULTS));
    }

    private Page<Document> rankByRating(DocumentAssistantQuery query) {
        List<Document> candidates = repository.findPublishedForRanking(query.keyword(), query.topic(),
                query.author(), query.languageCode(), query.year());
        if (candidates == null || candidates.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(query.page(), MAX_RESULTS), 0);
        }

        Map<Long, DocumentRatingSummary> summaries = new HashMap<>();
        if (reviewRepository != null) {
            List<Long> ids = candidates.stream().map(Document::getId).filter(java.util.Objects::nonNull).toList();
            reviewRepository.findVisibleRatingSummaries(ids).forEach(item -> summaries.put(item.documentId(), item));
        }

        Comparator<Document> comparator = Comparator
                .comparingDouble((Document document) -> ratingOf(summaries, document)).reversed()
                .thenComparing(Comparator.comparingLong((Document document) -> reviewCountOf(summaries, document)).reversed())
                .thenComparing(Comparator.comparingLong(Document::getViewCount).reversed())
                .thenComparing(Comparator.comparing(Document::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .thenComparing(Document::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        List<Document> sorted = candidates.stream().sorted(comparator).toList();
        int start = Math.min(query.page() * MAX_RESULTS, sorted.size());
        int end = Math.min(start + MAX_RESULTS, sorted.size());
        return new PageImpl<>(sorted.subList(start, end), PageRequest.of(query.page(), MAX_RESULTS), sorted.size());
    }

    private double ratingOf(Map<Long, DocumentRatingSummary> summaries, Document document) {
        DocumentRatingSummary summary = summaries.get(document.getId());
        return summary == null ? 0.0 : summary.averageRating();
    }

    private long reviewCountOf(Map<Long, DocumentRatingSummary> summaries, Document document) {
        DocumentRatingSummary summary = summaries.get(document.getId());
        return summary == null ? 0L : summary.reviewCount();
    }

    private Sort sortFor(DocumentAssistantQuery query) {
        if (query.sortMode().equals("downloaded")) {
            return Sort.by(Sort.Order.desc("downloadCount"), Sort.Order.desc("viewCount"),
                    Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        }
        if (query.sortMode().equals("popular")) {
            return Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("downloadCount"),
                    Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
        }
        return Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    }

    private boolean hasTextFilter(DocumentAssistantQuery query) {
        return !query.keyword().isEmpty() || !query.topic().isEmpty() || !query.author().isEmpty();
    }

    private boolean matches(Document document, DocumentAssistantQuery query) {
        if (!query.languageCode().isEmpty() && !query.languageCode().equalsIgnoreCase(safe(document.getLanguageCode()))) {
            return false;
        }
        if (query.year() != null && (document.getPublishedAt() == null
                || document.getPublishedAt().getYear() != query.year())) return false;
        if (!query.author().isEmpty() && !matchesText(query.author(), authorText(document))) return false;
        if (!query.topic().isEmpty() && !matchesAny(query.topic(), categoryText(document), document.getKeywords(),
                document.getTitle(), document.getSummary(), document.getDescription())) return false;
        return query.keyword().isEmpty() || matchesAny(query.keyword(), document.getTitle(), document.getKeywords(),
                categoryText(document), authorText(document), document.getSummary(), document.getDescription());
    }

    private Comparator<Document> documentComparator(DocumentAssistantQuery query) {
        Comparator<Document> newest = Comparator.comparing(Document::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Document::getId, Comparator.nullsLast(Comparator.reverseOrder()));
        if (query.sortMode().equals("newest")) return newest;
        if (query.sortMode().equals("popular")) {
            return Comparator.comparingLong(Document::getViewCount).reversed()
                    .thenComparing(Comparator.comparingLong(Document::getDownloadCount).reversed())
                    .thenComparing(newest);
        }
        return Comparator.comparingInt((Document document) -> relevanceScore(document, query)).reversed()
                .thenComparing(newest)
                .thenComparing(Comparator.comparingLong(
                        (Document document) -> document.getViewCount() + document.getDownloadCount()).reversed());
    }

    private int relevanceScore(Document document, DocumentAssistantQuery query) {
        String term = firstNonBlank(query.keyword(), query.topic(), query.author());
        int score = 0;
        if (matchesText(term, document.getTitle())) score = Math.max(score, 600);
        if (matchesText(term, document.getKeywords())) score = Math.max(score, 500);
        if (matchesText(term, categoryText(document))) score = Math.max(score, 400);
        if (matchesText(term, authorText(document))) score = Math.max(score, 300);
        if (matchesAny(term, document.getSummary(), document.getDescription())) score = Math.max(score, 200);
        if (fold(document.getTitle()).equals(fold(term))) score += 150;
        return score;
    }

    private boolean matchesAny(String query, String... fields) {
        return Arrays.stream(fields).anyMatch(field -> matchesText(query, field));
    }

    private boolean matchesText(String query, String field) {
        String needle = fold(query);
        String haystack = fold(field);
        if (needle.isEmpty() || haystack.isEmpty()) return false;
        if (haystack.contains(needle) || compact(haystack).contains(compact(needle))) return true;

        String[] queryTokens = needle.split(" ");
        String[] fieldTokens = haystack.split(" ");
        for (String queryToken : queryTokens) {
            boolean tokenMatch = Arrays.stream(fieldTokens)
                    .anyMatch(fieldToken -> approximatelyEqual(queryToken, fieldToken));
            if (!tokenMatch) return false;
        }
        return true;
    }

    private boolean approximatelyEqual(String left, String right) {
        if (left.equals(right) || left.length() >= 4 && right.contains(left)) return true;
        int longest = Math.max(left.length(), right.length());
        int threshold = longest >= 8 ? 2 : longest >= 3 ? 1 : 0;
        return threshold > 0 && Math.abs(left.length() - right.length()) <= threshold
                && editDistance(left, right, threshold) <= threshold;
    }

    private int editDistance(String left, String right, int limit) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            int rowMinimum = current[0];
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1]
                        + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), substitution);
                rowMinimum = Math.min(rowMinimum, current[column]);
            }
            if (rowMinimum > limit) return limit + 1;
            previous = current;
        }
        return previous[right.length()];
    }

    private String extractLanguageCode(String value) {
        if (ENGLISH.matcher(value).find()) return "en";
        if (VIETNAMESE.matcher(value).find()) return "vi";
        return "";
    }

    private String stripLanguageWords(String value) {
        return VIETNAMESE.matcher(ENGLISH.matcher(value).replaceAll(" ")).replaceAll(" ");
    }

    private Integer extractYear(String value) {
        var matcher = YEAR.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    /**
     * =========================================================================================
     * LỌC BỎ MỆNH ĐỀ NGHI VẤN / CÂU HỎI TỰ NHIÊN (QUESTION CLAUSE STRIPPING):
     * Giúp bóc tách chủ đề cốt lõi khi người dùng nhập câu hỏi tự nhiên dài.
     * Ví dụ:
     *   "spring security giúp ích gì trong việc bảo vệ phần" -> "spring security"
     *   "spring boot là gì" -> "spring boot"
     * Nhờ đó, cả luồng tìm kiếm metadata và Semantic Retrieval dự phòng đều nhận được
     * từ khóa chủ đề chuẩn xác, không bị loãng bởi các từ nối câu hỏi tự nhiên.
     * =========================================================================================
     */
    private String stripQuestionClauses(String text) {
        if (text == null || text.isBlank()) return "";
        return text.replaceAll("(?i)\\s+(?:giúp ích gì|giup ich gi|có tác dụng gì|co tac dung gi|vai trò gì|vai tro gi|có ý nghĩa gì|co y nghia gi|là gì|la gi|là sao|la sao|như thế nào|nhu the nao|ra sao|hoạt động như thế nào|hoat dong nhu the nao|trong việc.*|trong viec.*).*$", "")
                .strip();
    }

    private String extract(Pattern pattern, String value) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? cleanupKeyword(stripQuestionClauses(matcher.group(1))) : "";
    }

    private String sanitizeSearchText(String message) {
        String keyword = LEADING_REQUEST.matcher(message).replaceFirst("");
        keyword = LEADING_HAVE.matcher(keyword).replaceFirst("");
        keyword = NEWEST.matcher(keyword).replaceAll(" ");
        keyword = POPULAR.matcher(keyword).replaceAll(" ");
        keyword = stripQuestionClauses(keyword);
        return TRAILING_POLITENESS.matcher(keyword).replaceFirst("");
    }

    private String cleanupKeyword(String value) {
        String keyword = DOCUMENT_WORDS.matcher(value == null ? "" : value).replaceAll(" ");
        keyword = FILLER_WORDS.matcher(keyword).replaceAll(" ");
        return normalize(keyword.replace('%', ' ').replace('_', ' ').replace('"', ' '));
    }

    private String canonicalizeAlias(String value) {
        String folded = fold(value);
        if (folded.equals("springboot") || folded.equals("spring boot")) return "Spring Boot";
        if (folded.equals("springsecurity") || folded.equals("spring security")) return "Spring Security";
        if (folded.equals("csdl") || folded.equals("database")
                || folded.equals("db") || folded.equals("sql")) return "cơ sở dữ liệu";
        if (folded.equals("cntt")) return "công nghệ thông tin";
        return value;
    }

    private List<String> suggestionsFor(DocumentAssistantQuery query, List<Document> documents, boolean hasMore) {
        Set<String> suggestions = new LinkedHashSet<>();
        String seed = firstNonBlank(query.topic(), query.keyword());

        if (!documents.isEmpty()) {
            if (!seed.isEmpty() && !query.sortMode().equals("newest")) {
                suggestions.add("Tài liệu " + seed + " mới nhất");
            }
            if (!categoryText(documents.getFirst()).isEmpty()) suggestions.add("Tìm tài liệu cùng danh mục");
            if (!authorText(documents.getFirst()).isEmpty()) suggestions.add("Tìm tài liệu cùng tác giả");
            if (hasMore) suggestions.add("Xem thêm");
        } else {
            List<String> categories = repository.suggestPublishedCategories(seed, PageRequest.of(0, 3));
            if (categories != null) categories.forEach(category -> suggestions.add("Tìm tài liệu về " + category));
            String shortened = shorterKeyword(seed);
            if (!shortened.isEmpty()) suggestions.add("Tìm tài liệu về " + shortened);
        }

        DEFAULT_SUGGESTIONS.forEach(suggestions::add);
        return suggestions.stream().limit(4).toList();
    }

    private String shorterKeyword(String keyword) {
        String[] parts = normalize(keyword).split("\\s+");
        if (parts.length <= 1) return "";
        return String.join(" ", Arrays.copyOf(parts, parts.length - 1));
    }

    private String resultMessage(DocumentAssistantQuery query, long total) {
        if (query.page() > 0) return "Mình đã tìm thêm các tài liệu phù hợp cho bạn.";
        String subject = queryText(query);
        if (query.intent().equals("LATEST")) {
            return query.keyword().isEmpty() && query.topic().isEmpty()
                    ? "Đây là các tài liệu mới nhất trên EduRepo."
                    : "Mình tìm thấy một vài tài liệu phù hợp về " + subject
                    + ". Tài liệu mới nhất đang ở đầu danh sách.";
        }
        if (query.intent().equals("POPULAR")) {
            return "Đây là các tài liệu phù hợp đang được xem hoặc tải nhiều trên EduRepo.";
        }
        if (query.intent().equals("TOP_RATED")) {
            return "Đây là các tài liệu được đánh giá cao nhất trên EduRepo.";
        }
        if (query.intent().equals("TOP_DOWNLOADED")) {
            return "Đây là các tài liệu được tải nhiều nhất trên EduRepo.";
        }
        if (!query.author().isEmpty()) return "Mình tìm thấy " + total + " tài liệu của " + query.author() + ".";
        if (!query.topic().isEmpty()) return "Mình tìm thấy " + total + " tài liệu thuộc chủ đề " + query.topic() + ".";
        if (!query.languageCode().isEmpty()) {
            return "Mình tìm thấy " + total + " tài liệu " + languageLabel(query.languageCode()) + ".";
        }
        return "Mình tìm thấy " + total + " tài liệu phù hợp về " + subject + ".";
    }

    private String noResultMessage(DocumentAssistantQuery query) {
        return NO_RESULTS_MESSAGE.formatted(queryText(query));
    }

    private String queryText(DocumentAssistantQuery query) {
        String text = firstNonBlank(query.keyword(), query.topic(), query.author(),
                languageLabel(query.languageCode()), query.year() == null ? "" : String.valueOf(query.year()));
        return text.isEmpty() ? "tài liệu mới nhất" : text;
    }

    private String languageLabel(String languageCode) {
        return switch (languageCode) {
            case "en" -> "tiếng Anh";
            case "vi" -> "tiếng Việt";
            default -> "";
        };
    }

    private DocumentAssistantItem toItem(Document document) {
        String description = firstNonBlank(document.getSummary(), document.getDescription(),
                "Chưa có mô tả cho tài liệu này.");
        String category = firstNonBlank(categoryText(document), "Chưa phân loại");
        String author = firstNonBlank(authorText(document), "Chưa cập nhật");
        List<DocumentAssistantAction> actions = new ArrayList<>();
        if (!category.equals("Chưa phân loại")) {
            actions.add(new DocumentAssistantAction("Tìm tương tự", "Tìm tài liệu về " + category));
            actions.add(new DocumentAssistantAction("Cùng danh mục", "Tìm tài liệu về " + category));
        }
        if (!author.equals("Chưa cập nhật")) {
            actions.add(new DocumentAssistantAction("Cùng tác giả", "Tìm tài liệu của " + author));
        }
        return new DocumentAssistantItem(document.getId(), document.getTitle(), shorten(description, 180),
                category, author, document.getPublishedAt(), "/repository/" + document.getId(),
                actions.stream().limit(3).toList(),
                reviewRepository == null ? null : reviewRepository.averageVisibleRating(document.getId()),
                reviewRepository == null ? null : reviewRepository.countByDocumentIdAndHiddenFalse(document.getId()),
                document.getViewCount(), document.getDownloadCount());
    }

    private DocumentAssistantContext contextFor(DocumentAssistantQuery query, List<Document> documents,
                                                DocumentAssistantContext previous) {
        String anchorAuthor = previous.anchorAuthor();
        String anchorTopic = previous.anchorTopic();
        if (!documents.isEmpty()) {
            anchorAuthor = firstNonBlank(authorText(documents.getFirst()), anchorAuthor);
            anchorTopic = firstNonBlank(categoryText(documents.getFirst()), anchorTopic);
        }
        return new DocumentAssistantContext(query.keyword(), query.topic(), query.author(), query.languageCode(),
                query.year(), query.sortMode(), query.page(), anchorAuthor, anchorTopic);
    }

    private List<com.hieu.edurepo.dto.RagSource> metadataSources(List<Document> documents) {
        List<com.hieu.edurepo.dto.RagSource> list = new ArrayList<>();
        int idx = 1;
        for (Document document : documents) {
            list.add(new com.hieu.edurepo.dto.RagSource(
                    idx++,
                    document.getId(),
                    null,
                    document.getTitle(),
                    null,
                    "/repository/" + document.getId(),
                    shorten(firstNonBlank(document.getSummary(), document.getDescription()), 360),
                    null,
                    null
            ));
        }
        return list;
    }

    private String repositoryUrl(DocumentAssistantQuery query) {
        String keyword = firstNonBlank(query.keyword(), query.topic(), query.author(),
                query.year() == null ? "" : String.valueOf(query.year()));
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/repository");
        if (!keyword.isEmpty()) builder.queryParam("keyword", keyword);
        if (!query.languageCode().isEmpty()) builder.queryParam("languageCode", query.languageCode());
        return builder.build().encode().toUriString();
    }

    private String categoryText(Document document) {
        return document.getCategory() == null ? "" : safe(document.getCategory().getName());
    }

    private String authorText(Document document) {
        String creator = document.getCreatedBy() == null ? "" : safe(document.getCreatedBy().getFullName());
        return firstNonBlank(document.getAuthorName(), creator);
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private String fold(String value) {
        String normalized = Normalizer.normalize(safe(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ");
        return normalize(normalized);
    }

    private String compact(String value) {
        return value.replace(" ", "");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.strip();
        }
        return "";
    }

    private String shorten(String value, int maxLength) {
        String clean = normalize(value);
        if (clean.length() <= maxLength) return clean;
        int boundary = clean.lastIndexOf(' ', maxLength - 1);
        int end = boundary >= maxLength / 2 ? boundary : maxLength - 1;
        return clean.substring(0, end).stripTrailing() + "…";
    }

    @Override
    public void streamResponse(String message, DocumentAssistantContext suppliedContext, Long scopedDocumentId,
                               org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String messageId = "msg-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            try {
                DocumentAssistantContext context = suppliedContext == null ? DocumentAssistantContext.empty() : suppliedContext;
                String normalized = normalize(message);

                if (normalized.isEmpty()) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", EMPTY_MESSAGE)));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                    emitter.complete();
                    return;
                }
                if (normalized.length() > MAX_MESSAGE_LENGTH) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", "Nội dung tìm kiếm không được vượt quá " + MAX_MESSAGE_LENGTH + " ký tự.")));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                    emitter.complete();
                    return;
                }

                // =========================================================================
                // 1. SCOPED PDF MODE (Tra cứu trực tiếp trong tài liệu đang mở)
                // =========================================================================
                if (scopedDocumentId != null) {
                    Document scopedDoc = repository.findById(scopedDocumentId).orElse(null);
                    if (scopedDoc == null || scopedDoc.getStatus() != com.hieu.edurepo.enums.DocumentStatus.PUBLISHED) {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", "Tài liệu này không tồn tại hoặc chưa được công bố.")));
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                        emitter.complete();
                        return;
                    }

                    List<com.hieu.edurepo.dto.RagSearchResult> scopedResults = (retrievalService != null)
                            ? retrievalService.retrieveForDocument(scopedDocumentId, normalized)
                            : List.of();
                    var builtContext = contextBuilder.buildScopedContext(normalized, scopedDocumentId, scopedDoc.getTitle(), scopedResults);

                    // Gửi event START
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("start").data(java.util.Map.of(
                            "type", "START",
                            "messageId", messageId,
                            "scope", java.util.Map.of("type", "DOCUMENT", "documentId", scopedDocumentId, "title", scopedDoc.getTitle())
                    )));

                    // Gửi event CITATION nếu có nguồn tham chiếu
                    if (!builtContext.sources().isEmpty()) {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("citation").data(builtContext.sources()));
                    }

                    // Gửi các token sinh ra từ OpenAI qua SSE
                    if (openAiService != null && openAiService.isAvailable()) {
                        openAiService.streamChatCompletion(
                                builtContext.systemPrompt(),
                                builtContext.userPrompt(),
                                token -> {
                                    try {
                                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", token)));
                                    } catch (Exception ex) {
                                        throw new RuntimeException("Client disconnected", ex);
                                    }
                                },
                                () -> {
                                    try {
                                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of(
                                                "type", "DONE",
                                                "messageId", messageId,
                                                "allResultsUrl", "/view/" + scopedDoc.getId()
                                        )));
                                        emitter.complete();
                                    } catch (Exception ex) {
                                        emitter.completeWithError(ex);
                                    }
                                },
                                error -> {
                                    try {
                                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data(java.util.Map.of(
                                                "message", "Đã xảy ra sự cố khi kết nối tới mô hình AI. Vui lòng thử lại sau."
                                        )));
                                        emitter.complete();
                                    } catch (Exception ignored) {
                                        emitter.completeWithError(error);
                                    }
                                }
                        );
                    } else {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", "Hệ thống AI chưa sẵn sàng. Bạn vui lòng thử lại sau.")));
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                        emitter.complete();
                    }
                    return;
                }

                // =========================================================================
                // 2. GLOBAL CHAT MODE (Tra cứu toàn kho học liệu EduRepo)
                // =========================================================================
                if (GREETING.matcher(normalized).matches()) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("start").data(java.util.Map.of("type", "START", "messageId", messageId, "scope", java.util.Map.of("type", "GLOBAL"))));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", GREETING_MESSAGE)));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                    emitter.complete();
                    return;
                }
                if (OUT_OF_SCOPE.matcher(normalized).find()) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("start").data(java.util.Map.of("type", "START", "messageId", messageId, "scope", java.util.Map.of("type", "GLOBAL"))));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", OUT_OF_SCOPE_MESSAGE)));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of("type", "DONE", "messageId", messageId)));
                    emitter.complete();
                    return;
                }

                DocumentAssistantQuery query = analyze(normalized, context);

                // Tool candidate (weather, time)
                if (isToolCandidate(normalized) && toolExecutorService != null) {
                    var resp = respond(message, suppliedContext, null);
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("start").data(java.util.Map.of("type", "START", "messageId", messageId, "scope", java.util.Map.of("type", "GLOBAL"))));
                    if (resp.sources() != null && !resp.sources().isEmpty()) {
                        emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("citation").data(resp.sources()));
                    }
                    String ans = resp.message() != null ? resp.message() : "";
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", ans)));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of(
                            "type", "DONE",
                            "messageId", messageId,
                            "documents", resp.documents() != null ? resp.documents() : List.of()
                    )));
                    emitter.complete();
                    return;
                }

                // Global RAG
                List<com.hieu.edurepo.dto.RagSearchResult> ragResults = (retrievalService != null)
                        ? retrievalService.retrieve(normalized)
                        : List.of();
                if ((ragResults == null || ragResults.isEmpty()) && !query.topic().isBlank()) {
                    ragResults = retrievalService.retrieve(query.topic());
                }
                if ((ragResults == null || ragResults.isEmpty()) && !query.keyword().isBlank()) {
                    ragResults = retrievalService.retrieve(query.keyword());
                }
                if (ragResults == null) ragResults = List.of();

                var builtContext = contextBuilder.buildContext(normalized, ragResults);

                // Gửi event START
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("start").data(java.util.Map.of(
                        "type", "START",
                        "messageId", messageId,
                        "scope", java.util.Map.of("type", "GLOBAL")
                )));

                // Gửi event CITATION
                if (!builtContext.sources().isEmpty()) {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("citation").data(builtContext.sources()));
                }

                if (!ragResults.isEmpty() && openAiService != null && openAiService.isAvailable()) {
                    openAiService.streamChatCompletion(
                            builtContext.systemPrompt(),
                            builtContext.userPrompt(),
                            token -> {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", token)));
                                } catch (Exception ex) {
                                    throw new RuntimeException("Client disconnected", ex);
                                }
                            },
                            () -> {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of(
                                            "type", "DONE",
                                            "messageId", messageId,
                                            "allResultsUrl", repositoryUrl(query)
                                    )));
                                    emitter.complete();
                                } catch (Exception ex) {
                                    emitter.completeWithError(ex);
                                }
                            },
                            error -> {
                                try {
                                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data(java.util.Map.of(
                                            "message", "Đã xảy ra sự cố khi kết nối tới mô hình AI. Vui lòng thử lại sau."
                                    )));
                                    emitter.complete();
                                } catch (Exception ignored) {
                                    emitter.completeWithError(error);
                                }
                            }
                    );
                } else {
                    // Fallback to sync respond() và stream câu trả lời
                    var resp = respond(message, suppliedContext, null);
                    String ans = resp.message() != null ? resp.message() : "";
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("token").data(java.util.Map.of("content", ans)));
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("done").data(java.util.Map.of(
                            "type", "DONE",
                            "messageId", messageId,
                            "documents", resp.documents() != null ? resp.documents() : List.of(),
                            "allResultsUrl", resp.allResultsUrl() != null ? resp.allResultsUrl() : ""
                    )));
                    emitter.complete();
                }
            } catch (Exception e) {
                LOGGER.warn("SSE stream processing terminated or failed: {}", e.getMessage());
                try {
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().name("error").data(java.util.Map.of(
                            "message", "Đã xảy ra lỗi khi tạo câu trả lời. Vui lòng thử lại sau."
                    )));
                    emitter.complete();
                } catch (Exception ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
    }
}

