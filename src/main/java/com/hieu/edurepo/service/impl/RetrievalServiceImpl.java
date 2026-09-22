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
import com.hieu.edurepo.util.SearchTextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    // =========================================================================
    // Embedding Query Cache — tránh gọi lại OpenAI Embedding API cho cùng query
    // LRU cache tối đa 100 entries, mỗi entry tự hết hạn sau 5 phút
    // =========================================================================
    private static final int EMBEDDING_CACHE_MAX_SIZE = 100;
    private static final long EMBEDDING_CACHE_TTL_MS = 5 * 60 * 1000L; // 5 phút

    private final Map<String, CachedEmbedding> embeddingCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedEmbedding> eldest) {
                    return size() > EMBEDDING_CACHE_MAX_SIZE;
                }
            });

    // =========================================================================
    // Published Chunks Cache — tránh query DB + parse JSON vector mỗi lần retrieve
    // Cache được invalidate khi gọi invalidateChunkCache()
    // =========================================================================
    private final AtomicReference<CachedChunkIndex> chunkIndexRef = new AtomicReference<>();

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

        List<IndexedChunk> indexedChunks = getOrBuildChunkIndex();
        if (indexedChunks.isEmpty()) {
            LOGGER.debug("No published document chunks available in repository.");
            return List.of();
        }

        // 1. Thử Semantic Vector Search qua Embedding (với cache)
        List<Double> queryEmbedding = getCachedEmbedding(query);
        if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
            double[] queryVec = toDoubleArray(queryEmbedding);
            List<RagSearchResult> vectorResults = new ArrayList<>();

            for (IndexedChunk ic : indexedChunks) {
                if (ic.vector == null || ic.vector.length == 0) {
                    continue;
                }
                double similarity = cosineSimilarity(queryVec, ic.vector);
                if (similarity >= minSimilarity) {
                    vectorResults.add(new RagSearchResult(ic.chunk.getDocument(), ic.chunk, similarity));
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

        double lexicalThreshold = Math.min(minSimilarity, 0.30);
        for (IndexedChunk ic : indexedChunks) {
            double score = computeTextRelevance(queryWords, ic.chunk);
            if (score >= lexicalThreshold) {
                keywordResults.add(new RagSearchResult(ic.chunk.getDocument(), ic.chunk, score));
            }
        }

        keywordResults.sort(Comparator.comparingDouble(RagSearchResult::similarity).reversed());
        return keywordResults.stream().limit(topK).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagSearchResult> retrieveForDocument(Long documentId, String query) {
        return retrieveForDocument(documentId, query, ragProperties.getTopK(), ragProperties.getSimilarityThreshold());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RagSearchResult> retrieveForDocument(Long documentId, String query, int topK, double minSimilarity) {
        if (documentId == null || query == null || query.isBlank()) {
            return List.of();
        }

        List<IndexedChunk> indexedChunks = getOrBuildChunkIndex();
        // LỌC CỨNG (Hard Filter Scope) ngay tại Retrieval Layer
        List<IndexedChunk> docChunks = indexedChunks.stream()
                .filter(ic -> ic.chunk != null && ic.chunk.getDocument() != null && documentId.equals(ic.chunk.getDocument().getId()))
                .toList();

        if (docChunks.isEmpty()) {
            LOGGER.debug("No published document chunks available for document ID: {}", documentId);
            return List.of();
        }

        // 1. Semantic Vector Search qua Embedding
        List<Double> queryEmbedding = getCachedEmbedding(query);
        if (queryEmbedding != null && !queryEmbedding.isEmpty()) {
            double[] queryVec = toDoubleArray(queryEmbedding);
            List<RagSearchResult> vectorResults = new ArrayList<>();

            for (IndexedChunk ic : docChunks) {
                if (ic.vector == null || ic.vector.length == 0) {
                    continue;
                }
                double similarity = cosineSimilarity(queryVec, ic.vector);
                if (similarity >= minSimilarity) {
                    vectorResults.add(new RagSearchResult(ic.chunk.getDocument(), ic.chunk, similarity));
                }
            }

            if (!vectorResults.isEmpty()) {
                vectorResults.sort(Comparator.comparingDouble(RagSearchResult::similarity).reversed());
                return vectorResults.stream().limit(topK).toList();
            }
        }

        // 2. Fallback: Lexical Keyword Match trong chính document đó
        LOGGER.debug("Falling back to text similarity matching for scoped doc {}: {}", documentId, query);
        List<RagSearchResult> keywordResults = new ArrayList<>();
        Set<String> queryWords = extractWords(query);

        double lexicalThreshold = Math.min(minSimilarity, 0.25);
        for (IndexedChunk ic : docChunks) {
            double score = computeTextRelevance(queryWords, ic.chunk);
            if (score >= lexicalThreshold) {
                keywordResults.add(new RagSearchResult(ic.chunk.getDocument(), ic.chunk, score));
            }
        }

        keywordResults.sort(Comparator.comparingDouble(RagSearchResult::similarity).reversed());
        return keywordResults.stream().limit(topK).toList();
    }

    /**
     * Xóa cache chunk index — gọi phương thức này sau khi reindex tài liệu.
     */
    public void invalidateChunkCache() {
        chunkIndexRef.set(null);
        LOGGER.debug("Chunk index cache invalidated.");
    }


    // =========================================================================
    // Cache Management
    // =========================================================================

    private List<Double> getCachedEmbedding(String query) {
        String key = query.strip().toLowerCase(Locale.ROOT);
        CachedEmbedding cached = embeddingCache.get(key);
        if (cached != null && !cached.isExpired()) {
            LOGGER.debug("Embedding cache HIT for query: {}", key.substring(0, Math.min(key.length(), 30)));
            return cached.embedding;
        }
        // Cache MISS — gọi API
        List<Double> embedding = embeddingService.embedText(query);
        if (embedding != null && !embedding.isEmpty()) {
            embeddingCache.put(key, new CachedEmbedding(embedding, System.currentTimeMillis()));
        }
        return embedding;
    }

    private List<IndexedChunk> getOrBuildChunkIndex() {
        CachedChunkIndex current = chunkIndexRef.get();
        if (current != null) {
            return current.chunks;
        }

        // Build index: load từ DB + pre-parse JSON vectors
        List<DocumentChunk> rawChunks = chunkRepository.findAllPublishedChunks();
        List<IndexedChunk> indexed = new ArrayList<>(rawChunks.size());
        int vectorCount = 0;

        for (DocumentChunk chunk : rawChunks) {
            if (chunk.getDocument() == null) {
                LOGGER.debug("Skipping chunk ID {} with null document reference.", chunk.getId());
                continue;
            }
            double[] vector = parseVectorArray(chunk.getEmbeddingJson());
            indexed.add(new IndexedChunk(chunk, vector));
            if (vector != null && vector.length > 0) vectorCount++;
        }

        CachedChunkIndex newIndex = new CachedChunkIndex(Collections.unmodifiableList(indexed));
        chunkIndexRef.set(newIndex);
        LOGGER.info("Built chunk index: {} chunks ({} with vectors).", indexed.size(), vectorCount);
        return newIndex.chunks;
    }

    // =========================================================================
    // Vector & Similarity Utils
    // =========================================================================

    private double[] parseVectorArray(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Double> list = objectMapper.readValue(json, new TypeReference<List<Double>>() {});
            return toDoubleArray(list);
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] toDoubleArray(List<Double> list) {
        if (list == null) return null;
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /**
     * Cosine similarity sử dụng double[] thay vì List&lt;Double&gt; để tối ưu hiệu suất.
     */
    public static double cosineSimilarity(double[] v1, double[] v2) {
        if (v1 == null || v2 == null || v1.length == 0 || v1.length != v2.length) {
            return 0.0;
        }

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            normA += v1[i] * v1[i];
            normB += v2[i] * v2[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Backward-compatible overload cho unit tests hiện tại.
     */
    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        if (v1 == null || v2 == null || v1.isEmpty() || v1.size() != v2.size()) {
            return 0.0;
        }
        return cosineSimilarity(toDoubleArray(v1), toDoubleArray(v2));
    }

    /**
     * =========================================================================================
     * DANH SÁCH TỪ DỪNG (STOP WORDS) TIẾNG VIỆT VÀ TIẾNG ANH:
     * Tập hợp các hư từ, từ nối, trợ từ, liên từ và các từ ngữ mang tính chất câu hỏi tự nhiên.
     * 
     * TẠI SAO PHẢI CÓ DANH SÁCH NÀY? (GIẢI THÍCH NGUYÊN NHÂN LỖI):
     * Trước đây, khi người dùng gõ:
     *   "Tìm tài liệu về spring security giúp ích gì trong việc bảo vệ phần" (13 từ)
     * Hệ thống cũ duyệt từng từ và kiểm tra xem đoạn trích (chunk) có chứa từ đó không:
     *   fullText.contains(word)
     * Cuốn giáo trình "Nhập môn trí tuệ nhân tạo" (Doc 1) dài hàng trăm trang nên chứa đầy đủ
     * 10 từ dừng thông dụng: "tìm", "tài", "liệu", "về", "giúp", "ích", "gì", "trong", "việc", "phần".
     * Kết quả là Doc 1 đạt tỷ lệ khớp: 10/13 = 77% (0.77 điểm relevance)!
     * Mặc dù trong Doc 1 KHÔNG HỀ CÓ một chữ "spring" hay "security" nào, nó vẫn được chấm điểm cao
     * ngang ngửa tài liệu Spring Security và bị đẩy lên hàng đầu.
     * 
     * GIẢI PHÁP:
     * Lọc bỏ hoàn toàn các từ dừng này để tách riêng "Từ khóa cốt lõi" (Core Keywords) như:
     * "spring", "security", "boot"...
     * =========================================================================================
     */
    private static final Set<String> COMMON_STOP_WORDS = Set.of(
            "a", "about", "an", "and", "are", "at", "ban", "be",
            "been", "bi", "biet", "by", "cac", "cach", "can", "che", "cho", "chu",
            "chua", "co", "could", "cua", "cuu", "da", "dang", "day",
            "de", "did", "do", "doc", "does", "du", "dung", "duoc", "for", "gi",
            "giai", "giup", "has", "have", "hay", "he", "hieu", "hoac", "hoc", "hoi",
            "how", "huong", "ich", "in", "is", "it", "khai", "khong", "kia", "kiem",
            "la", "lam", "lieu", "minh", "mot", "muc", "nao", "nay", "nen",
            "nhan", "nhieu", "nhu", "nhung", "niem", "not", "of", "on", "or", "phan",
            "qua", "ra", "rat", "sao", "se", "should", "tai", "te", "the", "theo",
            "thich", "thong", "thuc", "tim", "to", "toi", "tong", "tra", "trong", "tu",
            "ung", "va", "vai", "ve", "vi", "viec", "voi", "was", "were", "what",
            "when", "where", "why", "with", "would", "xin"
    );

    /**
     * =========================================================================================
     * TÍNH TOÁN ĐỘ PHÙ HỢP TỪ VỰNG (LEXICAL RELEVANCE) CHO CHUNK:
     * 
     * CÁC BƯỚC NÂNG CẤP CHỐNG LỆCH CHỦ ĐỀ (ANTI-CONTAMINATION RETRIEVAL):
     * 1. BÓC TÁCH TỪ KHÓA CỐT LÕI (Core Keywords):
     *    Loại bỏ các từ dừng (Stop Words). Ví dụ câu hỏi về "spring security...", sau khi lọc
     *    chỉ còn lại 2 từ cốt lõi là ["spring", "security"].
     * 2. RÀNG BUỘC CỨNG (Hard Core Constraint):
     *    Nếu câu hỏi có từ khóa cốt lõi, chunk hoặc tài liệu BẮT BUỘC phải chứa từ khóa cốt lõi.
     *    Khi câu hỏi có từ 3 từ khóa cốt lõi trở lên, bắt buộc phải khớp tối thiểu 2 từ khóa
     *    để loại trừ trường hợp trùng ngẫu nhiên 1 từ đơn lẻ trong tài liệu khác chủ đề.
     *    Nếu không đủ số từ khóa tối thiểu, ĐIỂM SẼ BẰNG 0.0 NGAY LẬP TỨC!
     * 3. SO KHỚP CHÍNH XÁC THEO TOKEN (Whole Word Matching):
     *    Sử dụng Set.contains thay vì String.contains để tránh so khớp nhầm các từ con.
     * 4. TÍNH ĐIỂM TRỌNG SỐ ĐA TẦNG:
     *    - Độ phủ từ khóa cốt lõi (Core Ratio): chiếm 60% tổng điểm.
     *    - Điểm cộng xuất hiện trong Tiêu đề tài liệu (Title Bonus): tối đa +25%.
     *    - Điểm cộng xuất hiện trực tiếp trong Đoạn trích (Chunk Bonus): tối đa +15%.
     * =========================================================================================
     */
    private double computeTextRelevance(Set<String> queryWords, DocumentChunk chunk) {
        if (queryWords == null || queryWords.isEmpty() || chunk == null || chunk.getContent() == null) {
            return 0.0;
        }

        // BƯỚC 1: Lọc bỏ từ dừng để lấy tập từ khóa nghiệp vụ cốt lõi
        Set<String> coreKeywords = queryWords.stream()
                .filter(w -> !COMMON_STOP_WORDS.contains(w))
                .collect(Collectors.toSet());

        // Nếu tất cả các từ trong câu hỏi đều là từ dừng (ví dụ người dùng chỉ gõ "tài liệu"),
        // fallback sử dụng lại toàn bộ queryWords để không làm rỗng truy vấn.
        if (coreKeywords.isEmpty()) {
            coreKeywords = queryWords;
        }

        Document doc = chunk.getDocument();
        // Tách từ theo token riêng biệt cho từng phần (tránh khớp xâu con sai lệch)
        Set<String> chunkWords = extractWords(chunk.getContent());
        Set<String> titleWords = doc != null && doc.getTitle() != null ? extractWords(doc.getTitle()) : Collections.emptySet();
        Set<String> keywordWords = doc != null && doc.getKeywords() != null ? extractWords(doc.getKeywords()) : Collections.emptySet();

        Set<String> allTokens = new HashSet<>(chunkWords);
        allTokens.addAll(titleWords);
        allTokens.addAll(keywordWords);

        // BƯỚC 2 (RÀNG BUỘC CỨNG): Kiểm tra xem có khớp từ khóa cốt lõi nào không
        long matchedCore = coreKeywords.stream().filter(allTokens::contains).count();
        long minRequiredMatches = coreKeywords.size() <= 2 ? 1 : 2;
        if (matchedCore < minRequiredMatches) {
            // Không đủ số lượng từ khóa chuyên môn cốt lõi -> Loại bỏ ngay lập tức
            return 0.0;
        }

        // BƯỚC 3: Tính điểm dựa trên tỷ lệ bao phủ từ khóa cốt lõi
        double coreRatio = (double) matchedCore / coreKeywords.size();

        // Ưu tiên cao: Nếu từ khóa cốt lõi xuất hiện ngay trong Tiêu đề tài liệu (Title Bonus)
        long titleMatched = coreKeywords.stream().filter(titleWords::contains).count();
        double titleBonus = titleMatched > 0 ? 0.25 * ((double) titleMatched / coreKeywords.size()) : 0.0;

        // Ưu tiên: Nếu từ khóa cốt lõi xuất hiện trực tiếp trong nội dung của đoạn trích (Chunk Bonus)
        long chunkMatched = coreKeywords.stream().filter(chunkWords::contains).count();
        double chunkBonus = chunkMatched > 0 ? 0.15 * ((double) chunkMatched / coreKeywords.size()) : 0.0;

        // Tổng hợp điểm số (tối đa là 1.0)
        return Math.min(1.0, (coreRatio * 0.6) + titleBonus + chunkBonus);
    }

    private Set<String> extractWords(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(SearchTextNormalizer.fold(text).split("[^a-z0-9]+"))
                .filter(w -> w.length() > 1)
                .collect(Collectors.toSet());
    }

    // =========================================================================
    // Inner Cache Records
    // =========================================================================

    private record CachedEmbedding(List<Double> embedding, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > EMBEDDING_CACHE_TTL_MS;
        }
    }

    private record IndexedChunk(DocumentChunk chunk, double[] vector) {}

    private record CachedChunkIndex(List<IndexedChunk> chunks) {}
}
