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
            LOGGER.info("Khoi tao du lieu mau bi tat theo cau hinh (app.seed.sample-data=false).");
            return;
        }

        try {
            long totalDownloads = documentRepository.sumDownloadCount();
            long totalReviews = reviewRepository.count();

            // Tu dong chay khi kho hoc lieu chua co luot tai hoac chua co binh luan/danh gia
            if (totalDownloads == 0 || totalReviews == 0) {
                LOGGER.info("Phat hien he thong chua co luot tai hoac danh gia (downloads={}, reviews={}). Dang tao du lieu ao...",
                        totalDownloads, totalReviews);
                int count = sampleDataService.seedSampleDocuments(true);
                LOGGER.info("Da lam giau tuong tac thanh cong cho {} tai lieu.", count);
            } else {
                LOGGER.info("Kho hoc lieu da co du lieu thong ke tuong tac day du (downloads={}, reviews={}).",
                        totalDownloads, totalReviews);
            }
        } catch (Exception e) {
            LOGGER.warn("Khong the khoi tao du lieu tuong tac mau: {}", e.getMessage(), e);
        }
    }
}
