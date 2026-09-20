package com.hieu.edurepo.config;

import com.hieu.edurepo.repository.DocumentChunkRepository;
import com.hieu.edurepo.service.DocumentIndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Khởi động chỉ mục RAG (Retrieval-Augmented Generation) ngay sau khi ứng dụng chạy.
 *
 * <p>Thực hiện sau khi Spring Boot khởi động xong (ý nghĩa {@link ApplicationRunner}):
 * nếu bảng {@code document_chunks} trong CSDL đang trống, sẽ tự động chạy reindex
 * toàn bộ tài liệu đã công bố trong background thread.</p>
 *
 * <p>Mục đích: đảm bảo chatbot có dữ liệu để tìm kiếm ngay cả khi deploy lần đầu
 * hoặc sau khi xóa sạch database. Chạy phi đồng bộ (ánh xạ background thread) để
 * không ảnh hưởng tới thời gian khởi động ứng dụng.</p>
 *
 * <p>Bị bỏ qua hoàn toàn nếu RAG được tắt ({@code rag.enabled=false}).</p>
 */
@Component
public class RagIndexInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RagIndexInitializer.class);

    private final DocumentChunkRepository chunkRepository;
    private final DocumentIndexingService indexingService;
    private final RagProperties ragProperties;

    public RagIndexInitializer(DocumentChunkRepository chunkRepository,
                               DocumentIndexingService indexingService,
                               RagProperties ragProperties) {
        this.chunkRepository = chunkRepository;
        this.indexingService = indexingService;
        this.ragProperties = ragProperties;
    }

    /**
     * Callback chạy sau khi Spring Boot context khởi động xong.
     *
     * <p>Logic:
     * - Nếu RAG bị tắt: ghi log và thoát sớm.
     * - Nếu chưa có chunk nào trong CSDL: khởi chạy reindex background.
     * - Nếu đã có chunk: ghi log số lượng và tiếp tục bình thường.
     * </p>
     *
     * @param args Tham số khởi động (không sử dụng).
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!ragProperties.isEnabled()) {
            LOGGER.info("RAG is disabled by configuration.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                long currentChunks = chunkRepository.count();
                if (currentChunks == 0) {
                    LOGGER.info("No document chunks detected in RAG index. Triggering initial background indexing...");
                    int indexed = indexingService.reindexAllPublishedDocuments();
                    LOGGER.info("Initial RAG indexing completed: {} documents indexed.", indexed);
                } else {
                    LOGGER.info("RAG index ready with {} existing chunks.", currentChunks);
                }
            } catch (Exception e) {
                LOGGER.warn("Initial RAG background indexing encountered an issue: {}", e.getMessage());
            }
        });
    }
}
