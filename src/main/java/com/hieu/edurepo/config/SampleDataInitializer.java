package com.hieu.edurepo.config;

import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.service.SampleDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Trình khởi tạo dữ liệu tương tác mẫu (Sample Data Initializer).
 * <p>
 * Tự động chạy khi khởi động ứng dụng (Order 2): Nếu phát hiện kho học liệu chưa có số liệu thống kê
 * (lượt tải bằng 0 hoặc chưa có đánh giá nhận xét), hệ thống tự động làm giàu dữ liệu demo
 * để giao diện Dashboard và thẻ tài liệu hiển thị đẹp mắt, trực quan.
 * </p>
 */
@Component
@Order(2)
public class SampleDataInitializer implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleDataInitializer.class);

    private final SampleDataService sampleDataService;
    private final DocumentRepository documentRepository;
    private final DocumentReviewRepository reviewRepository;
    private final boolean seedEnabled;

    public SampleDataInitializer(SampleDataService sampleDataService,
                                  DocumentRepository documentRepository,
                                  DocumentReviewRepository reviewRepository,
                                  @Value("${app.seed.sample-data:true}") boolean seedEnabled) {
        this.sampleDataService = sampleDataService;
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(String... args) {
        if (!seedEnabled) {
            LOGGER.info("Khởi tạo dữ liệu mẫu bị tắt theo cấu hình (app.seed.sample-data=false).");
            return;
        }

        try {
            long totalDownloads = documentRepository.sumDownloadCount();
            long totalReviews = reviewRepository.count();

            // Tự động chạy khi kho học liệu chưa có lượt tải hoặc chưa có bình luận/đánh giá
            if (totalDownloads == 0 || totalReviews == 0) {
                LOGGER.info("Phát hiện hệ thống chưa có lượt tải hoặc đánh giá (downloads={}, reviews={}). Đang tạo dữ liệu mẫu...",
                        totalDownloads, totalReviews);
                int count = sampleDataService.seedSampleDocuments(true);
                LOGGER.info("Đã làm giàu tương tác thành công cho {} tài liệu học liệu mẫu.", count);
            } else {
                LOGGER.info("Kho học liệu đã có dữ liệu thống kê tương tác đầy đủ (downloads={}, reviews={}).",
                        totalDownloads, totalReviews);
            }
        } catch (Exception e) {
            LOGGER.warn("Không thể khởi tạo dữ liệu tương tác mẫu: {}", e.getMessage(), e);
        }
    }
}
