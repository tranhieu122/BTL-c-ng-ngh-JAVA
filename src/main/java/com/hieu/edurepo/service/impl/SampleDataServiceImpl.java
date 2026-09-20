package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentDownloadHistory;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.DocumentViewHistory;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentDownloadHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.DocumentViewHistoryRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.DocumentIndexingService;
import com.hieu.edurepo.service.SampleDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class SampleDataServiceImpl implements SampleDataService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SampleDataServiceImpl.class);

    private final DocumentRepository documentRepository;
    private final DocumentReviewRepository reviewRepository;
    private final DocumentViewHistoryRepository viewHistoryRepository;
    private final DocumentDownloadHistoryRepository downloadHistoryRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentIndexingService documentIndexingService;

    public SampleDataServiceImpl(DocumentRepository documentRepository,
                                  DocumentReviewRepository reviewRepository,
                                  DocumentViewHistoryRepository viewHistoryRepository,
                                  DocumentDownloadHistoryRepository downloadHistoryRepository,
                                  UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Autowired(required = false) DocumentIndexingService documentIndexingService) {
        this.documentRepository = documentRepository;
        this.reviewRepository = reviewRepository;
        this.viewHistoryRepository = viewHistoryRepository;
        this.downloadHistoryRepository = downloadHistoryRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.documentIndexingService = documentIndexingService;
    }

    @Override
    @Transactional
    public int seedSampleDocuments(boolean force) {
        LOGGER.info("Bắt đầu tạo dữ liệu ảo: lượt xem, lượt tải và bình luận / đánh giá cho tài liệu...");

        List<Document> allDocs = documentRepository.findAll();
        if (allDocs.isEmpty()) {
            LOGGER.info("Kho tài liệu hiện tại chưa có tài liệu nào để tạo tương tác ảo.");
            return 0;
        }

        // 1. Tạo danh sách các tài khoản người đọc ảo để bình luận
        List<User> reviewers = getOrCreateSampleReviewers();

        // 2. Làm giàu lượt xem, lượt tải và từ khóa tìm kiếm cho toàn bộ tài liệu hiện có
        int enrichedCount = 0;
        for (Document doc : allDocs) {
            boolean changed = false;
            long docId = doc.getId() != null ? doc.getId() : 1L;

            // Giả lập lượt tải: từ 25 đến 220 lượt tải (không còn bị 0)
            if (doc.getDownloadCount() <= 0 && doc.getStatus() == DocumentStatus.PUBLISHED) {
                long simulatedDownloads = 25 + (docId * 13) % 195;
                doc.setDownloadCount(simulatedDownloads);
                changed = true;
            }

            // Giả lập lượt xem: luôn lớn hơn lượt tải (từ 80 đến 650 lượt xem)
            long minViews = doc.getDownloadCount() * 2 + 50;
            if (doc.getViewCount() < minViews && doc.getStatus() == DocumentStatus.PUBLISHED) {
                long simulatedViews = minViews + (docId * 17) % 250;
                doc.setViewCount(simulatedViews);
                changed = true;
            }

            // Bổ sung từ khóa tìm kiếm nếu chưa có để khi tìm kiếm luôn thấy
            if (doc.getKeywords() == null || doc.getKeywords().isBlank()) {
                doc.setKeywords(generateKeywords(doc));
                changed = true;
            }

            // Bổ sung tóm tắt nếu chưa có
            if (doc.getSummary() == null || doc.getSummary().isBlank()) {
                if (doc.getDescription() != null && !doc.getDescription().isBlank()) {
                    doc.setSummary(doc.getDescription());
                } else {
                    doc.setSummary("Tài liệu học tập và nghiên cứu lưu trữ tại EduRepo: " + doc.getTitle());
                }
                changed = true;
            }

            if (changed) {
                documentRepository.save(doc);
                enrichedCount++;
            }
        }

        // 3. Tạo các bình luận và đánh giá học thuật sinh động cho các tài liệu
        int reviewsCreated = seedDocumentReviews(allDocs, reviewers);

        // 4. Tạo lịch sử xem và tải về gần đây
        seedUserHistories(allDocs, reviewers);

        // 5. Đồng bộ RAG Semantic Search để tìm kiếm chắc chắn thấy
        if (documentIndexingService != null) {
            try {
                LOGGER.info("Lập chỉ mục RAG Semantic Search cho toàn bộ học liệu...");
                documentIndexingService.reindexAllPublishedDocuments();
            } catch (Exception e) {
                LOGGER.warn("Lỗi khi lập chỉ mục RAG: {}", e.getMessage());
            }
        }

        LOGGER.info("Hoàn tất tạo dữ liệu ảo: Cập nhật {} tài liệu (lượt xem & tải), tạo {} bình luận/đánh giá.",
                enrichedCount, reviewsCreated);
        return enrichedCount;
    }

    private List<User> getOrCreateSampleReviewers() {
        Role userRole = roleRepository.findByName(RoleName.USER).orElseGet(() -> {
            Role r = new Role(RoleName.USER);
            return roleRepository.save(r);
        });

        String[][] reviewerData = {
                {"Nguyễn Văn An", "nguyenvanan@edurepo.local"},
                {"Trần Thị Bích", "tranthibich@edurepo.local"},
                {"Lê Hoàng Cường", "lehoangcuong@edurepo.local"},
                {"Phạm Thị Dung", "phamthidung@edurepo.local"},
                {"Vũ Quang Hiếu", "vuquanghieu@edurepo.local"},
                {"Đặng Thu Hà", "dangthuha@edurepo.local"},
                {"Hoàng Minh Tuấn", "hoangminhtuan@edurepo.local"},
                {"Bùi Mai Anh", "buimaianh@edurepo.local"},
                {"Đỗ Đức Trọng", "doductrong@edurepo.local"},
                {"Ngô Thảo Nguyên", "ngothaonguyen@edurepo.local"},
                {"Dương Quốc Huy", "duongquochuy@edurepo.local"},
                {"Lý Thanh Thảo", "lythanhthao@edurepo.local"}
        };

        List<User> list = new ArrayList<>();
        for (String[] data : reviewerData) {
            String name = data[0];
            String email = data[1];

            User user = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
                User u = new User();
                u.setUsername(email);
                u.setEmail(email);
                u.setFullName(name);
                u.setPassword(passwordEncoder.encode("User@123456"));
                u.setEnabled(true);
                u.setRoles(Set.of(userRole));
                return userRepository.save(u);
            });
            list.add(user);
        }
        return list;
    }

    private int seedDocumentReviews(List<Document> documents, List<User> reviewers) {
        if (reviewers.isEmpty()) return 0;

        String[] positiveComments = {
                "Tài liệu trình bày rất mạch lạc, ví dụ minh họa trực quan dễ hiểu. Cảm ơn tác giả đã chia sẻ!",
                "Nội dung bám sát đề cương môn học, bài tập thực hành rất sát với thực tế.",
                "Giáo trình rất chất lượng, mình đã áp dụng làm đồ án môn học đạt kết quả xuất sắc.",
                "Slide và hình vẽ minh họa chi tiết, cực kỳ hữu ích cho sinh viên ôn thi cuối kỳ.",
                "Kiến thức nền tảng vững chắc, phần giải thích các khái niệm phức tạp rất dễ tiếp thu.",
                "Tài liệu hay, biên soạn khoa học, xứng đáng 5 sao!",
                "Học liệu rất thực chiến, các sơ đồ hệ thống và phân tích ca sử dụng rất trực quan.",
                "Nội dung cập nhật mới, giải thích rõ ràng và có nhiều tài liệu tham khảo giá trị.",
                "Rất khuyến khích các bạn khóa dưới tải về tham khảo để nắm chắc kiến thức chuyên ngành.",
                "Bố cục rõ ràng, văn phong mạch lạc, đọc rất cuốn và dễ thực hành theo.",
                "Bài giảng đầy đủ từ cơ bản đến nâng cao, giải quyết đúng những thắc mắc mình gặp phải.",
                "Chất lượng tệp rất rõ nét, trình bày chuyên nghiệp chuẩn học thuật."
        };

        int count = 0;
        for (Document doc : documents) {
            if (doc.getStatus() != DocumentStatus.PUBLISHED) continue;

            // Mỗi tài liệu tạo từ 2 đến 4 bình luận ngẫu nhiên
            long docId = doc.getId() != null ? doc.getId() : 1L;
            int numReviews = 2 + (int) (docId % 3);

            for (int i = 0; i < numReviews; i++) {
                User reviewer = reviewers.get((int) ((docId + i * 3) % reviewers.size()));

                // Tránh tạo trùng do uk_document_review_user
                if (reviewRepository.existsByDocumentIdAndUserId(doc.getId(), reviewer.getId())) {
                    continue;
                }

                DocumentReview review = new DocumentReview();
                review.setDocument(doc);
                review.setUser(reviewer);
                // 85% 5 sao, 15% 4 sao
                int rating = ((docId + i) % 7 == 0) ? 4 : 5;
                review.setRating(rating);

                String comment = positiveComments[(int) ((docId + i * 5) % positiveComments.length)];
                review.setComment(comment);
                review.setHelpful(true);
                review.setEasyToUnderstand(true);
                review.setOnTopic(true);
                review.setGoodFileQuality(true);
                review.setHidden(false);

                reviewRepository.save(review);
                count++;
            }
        }
        return count;
    }

    private void seedUserHistories(List<Document> documents, List<User> reviewers) {
        List<Document> published = documents.stream()
                .filter(d -> d.getStatus() == DocumentStatus.PUBLISHED)
                .limit(10)
                .toList();
        if (published.isEmpty() || reviewers.isEmpty()) return;

        for (int i = 0; i < Math.min(3, reviewers.size()); i++) {
            User user = reviewers.get(i);
            for (int j = 0; j < Math.min(5, published.size()); j++) {
                Document doc = published.get(j);

                if (viewHistoryRepository.findByUserIdAndDocumentId(user.getId(), doc.getId()).isEmpty()) {
                    DocumentViewHistory view = new DocumentViewHistory();
                    view.setUser(user);
                    view.setDocument(doc);
                    view.setViewedAt(LocalDateTime.now().minusHours(j * 4 + 1));
                    viewHistoryRepository.save(view);
                }

                if (j % 2 == 0 && downloadHistoryRepository.findByUserIdAndDocumentId(user.getId(), doc.getId()).isEmpty()) {
                    DocumentDownloadHistory dl = new DocumentDownloadHistory();
                    dl.setUser(user);
                    dl.setDocument(doc);
                    dl.setDownloadedAt(LocalDateTime.now().minusHours(j * 6 + 2));
                    dl.setDownloadCount(1 + (j % 3));
                    downloadHistoryRepository.save(dl);
                }
            }
        }
    }

    private String generateKeywords(Document doc) {
        List<String> list = new ArrayList<>();
        if (doc.getTitle() != null) {
            String[] tokens = doc.getTitle().split("[\\s,._:;()/-]+");
            for (String token : tokens) {
                if (token.length() >= 3 && !list.contains(token)) {
                    list.add(token);
                }
            }
        }
        if (doc.getCategory() != null && !list.contains(doc.getCategory().getName())) {
            list.add(doc.getCategory().getName());
        }
        if (doc.getDepartment() != null && !list.contains(doc.getDepartment().getName())) {
            list.add(doc.getDepartment().getName());
        }
        return String.join(", ", list);
    }
}
