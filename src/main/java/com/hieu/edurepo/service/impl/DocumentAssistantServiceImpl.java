package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.dto.DocumentAssistantAction;
import com.hieu.edurepo.dto.DocumentAssistantContext;
import com.hieu.edurepo.dto.DocumentAssistantItem;
import com.hieu.edurepo.dto.DocumentAssistantQuery;
import com.hieu.edurepo.dto.DocumentAssistantResponse;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.repository.DocumentAssistantRepository;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class DocumentAssistantServiceImpl implements DocumentAssistantService {

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
            "\\b(thời tiết|thoi tiet|tin tức|tin tuc|mấy giờ|may gio|dịch sang|dich sang|viết (?:bài|code|mã)|viet (?:bai|code|ma)|làm bài|lam bai|giải bài|giai bai|tóm tắt|tom tat|soạn|soan|sáng tác|sang tac|kể chuyện|ke chuyen|nấu ăn|nau an)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NEWEST = Pattern.compile(
            "\\b(mới nhất|moi nhat|mới gần đây|moi gan day|gần đây|gan day|vừa đăng|vua dang|vừa xuất bản|vua xuat ban|tài liệu mới|tai lieu moi)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern POPULAR = Pattern.compile(
            "\\b(phổ biến|pho bien|xem nhiều|xem nhieu|được xem nhiều|duoc xem nhieu|tải nhiều|tai nhieu|được tải nhiều|duoc tai nhieu|nhiều lượt|nhieu luot|hot)\\b",
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

    public DocumentAssistantServiceImpl(DocumentAssistantRepository repository) {
        this(repository, null, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentAssistantServiceImpl(DocumentAssistantRepository repository,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.RetrievalService retrievalService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.ContextBuilder contextBuilder,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.service.OpenAIService openAiService,
                                       @org.springframework.beans.factory.annotation.Autowired(required = false) com.hieu.edurepo.config.RagProperties ragProperties) {
        this.repository = repository;
        this.retrievalService = retrievalService;
        this.contextBuilder = contextBuilder;
        this.openAiService = openAiService;
        this.ragProperties = ragProperties;
    }

    @Override
    public DocumentAssistantResponse respond(String message) {
        return respond(message, DocumentAssistantContext.empty());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentAssistantResponse respond(String message, DocumentAssistantContext suppliedContext) {
        DocumentAssistantContext context = suppliedContext == null ? DocumentAssistantContext.empty() : suppliedContext;
        String normalized = normalize(message);
        if (normalized.isEmpty()) {
            return messageWithContext("INVALID_INPUT", EMPTY_MESSAGE, List.of(), context);
        }
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            return messageWithContext("INVALID_INPUT",
                    "Nội dung tìm kiếm không được vượt quá " + MAX_MESSAGE_LENGTH + " ký tự.", List.of(), context);
        }
        if (GREETING.matcher(normalized).matches()) {
            return messageWithContext("GREETING", GREETING_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }
        if (OUT_OF_SCOPE.matcher(normalized).find()) {
            return messageWithContext("OUT_OF_SCOPE", OUT_OF_SCOPE_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }

        // =========================================================================
        // RAG (Retrieval-Augmented Generation) Pipeline with GPT-5.6 Luna
        // =========================================================================
        if (ragProperties != null && ragProperties.isEnabled() && retrievalService != null && contextBuilder != null) {
            List<com.hieu.edurepo.dto.RagSearchResult> ragResults = retrievalService.retrieve(normalized);

            if (ragResults != null && !ragResults.isEmpty()) {
                var builtContext = contextBuilder.buildContext(normalized, ragResults);

                List<Document> sourceDocuments = ragResults.stream()
                        .map(com.hieu.edurepo.dto.RagSearchResult::document)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList();
                List<DocumentAssistantItem> documents = sourceDocuments.stream().map(this::toItem).toList();

                if (openAiService != null && openAiService.isAvailable()) {
                    String llmAnswer = openAiService.generateChatCompletion(builtContext.systemPrompt(), builtContext.userPrompt());
                    if (llmAnswer != null && !llmAnswer.isBlank()) {
                        return new DocumentAssistantResponse("RAG_ANSWER", llmAnswer, normalized,
                                java.util.Map.of("model", "gpt-5.6-luna", "rag", "active"),
                                documents, List.of("Tài liệu mới nhất", "Tìm tài liệu tương tự"),
                                false, repositoryUrl(analyze(normalized, context)), context);
                    }
                }

                // Fallback khi OpenAI API chưa sẵn sàng hoặc gặp sự cố: trả kết quả Semantic Retrieval
                String fallbackMessage = "Dưới đây là các tài liệu trong EduRepo có nội dung liên quan phù hợp nhất với câu hỏi của bạn:";
                return new DocumentAssistantResponse("RESULTS", fallbackMessage, normalized,
                        java.util.Map.of("retrieval", "semantic"), documents,
                        suggestionsFor(analyze(normalized, context), sourceDocuments, false),
                        false, repositoryUrl(analyze(normalized, context)), context);
            } else {
                // CHỐNG HALLUCINATION: Không tìm thấy tài liệu phù hợp trong kho học liệu EduRepo
                return messageWithContext("NO_RESULTS", com.hieu.edurepo.dto.RagAnswer.NOT_FOUND_MESSAGE, DEFAULT_SUGGESTIONS, context);
            }
        }

        DocumentAssistantQuery query = analyze(normalized, context);
        if (query.intent().equals("SEARCH") && query.keyword().isEmpty()
                && query.topic().isEmpty() && query.author().isEmpty()
                && query.languageCode().isEmpty() && query.year() == null) {
            return messageWithContext("INVALID_INPUT", EMPTY_MESSAGE, DEFAULT_SUGGESTIONS, context);
        }

        Page<Document> page = search(query);
        if (page.isEmpty()) {
            DocumentAssistantContext nextContext = contextFor(query, List.of(), context);
            return new DocumentAssistantResponse("NO_RESULTS", noResultMessage(query), queryText(query),
                    query.filters(), List.of(), suggestionsFor(query, List.of(), false), false,
                    repositoryUrl(query), nextContext);
        }

        List<Document> sourceDocuments = page.getContent();
        List<DocumentAssistantItem> documents = sourceDocuments.stream().map(this::toItem).toList();
        DocumentAssistantContext nextContext = contextFor(query, sourceDocuments, context);
        return new DocumentAssistantResponse("RESULTS", resultMessage(query, page.getTotalElements()),
                queryText(query), query.filters(), documents, suggestionsFor(query, sourceDocuments, page.hasNext()),
                page.hasNext(), repositoryUrl(query), nextContext);
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
        String sortMode = POPULAR.matcher(message).find() ? "popular"
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
        if (query.sortMode().equals("popular") || query.sortMode().equals("newest")) {
            return repository.searchPublishedStructured(query.keyword(), query.topic(), query.author(),
                    query.languageCode(), query.year(), PageRequest.of(query.page(), MAX_RESULTS, sortFor(query)));
        }
        return repository.searchPublishedRelevant(query.keyword(), query.topic(), query.author(),
                query.languageCode(), query.year(), PageRequest.of(query.page(), MAX_RESULTS));
    }

    private Sort sortFor(DocumentAssistantQuery query) {
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

    private String extract(Pattern pattern, String value) {
        var matcher = pattern.matcher(value);
        return matcher.find() ? cleanupKeyword(matcher.group(1)) : "";
    }

    private String sanitizeSearchText(String message) {
        String keyword = LEADING_REQUEST.matcher(message).replaceFirst("");
        keyword = LEADING_HAVE.matcher(keyword).replaceFirst("");
        keyword = NEWEST.matcher(keyword).replaceAll(" ");
        keyword = POPULAR.matcher(keyword).replaceAll(" ");
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
                actions.stream().limit(3).toList());
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
}
