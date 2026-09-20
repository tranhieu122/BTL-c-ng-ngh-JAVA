package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SampleDataServiceTest {

    @Autowired
    private SampleDataService sampleDataService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Document testDoc;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName(RoleName.USER).orElseGet(() -> {
            Role r = new Role(RoleName.USER);
            return roleRepository.save(r);
        });

        User author = userRepository.findByEmailIgnoreCase("test.author@edurepo.local").orElseGet(() -> {
            User u = new User();
            u.setUsername("test.author@edurepo.local");
            u.setEmail("test.author@edurepo.local");
            u.setPassword("password");
            u.setFullName("Tác giả kiểm thử");
            u.setEnabled(true);
            u.getRoles().add(userRole);
            return userRepository.save(u);
        });

        Category cat = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category c = new Category();
            c.setName("Công nghệ thông tin");
            c.setActive(true);
            return categoryRepository.save(c);
        });

        testDoc = new Document();
        testDoc.setTitle("Giao trinh Kiem thu phan mem nang cao");
        testDoc.setStatus(DocumentStatus.PUBLISHED);
        testDoc.setCreatedBy(author);
        testDoc.setCategory(cat);
        testDoc.setViewCount(0);
        testDoc.setDownloadCount(0);
        testDoc = documentRepository.save(testDoc);
    }

    @Test
    void testSeedViewsDownloadsAndReviews() {
        // 1. Thực hiện tạo dữ liệu tương tác ảo
        int enriched = sampleDataService.seedSampleDocuments(true);
        assertTrue(enriched >= 1, "Should enrich at least the test document");

        Document updated = documentRepository.findById(testDoc.getId()).orElseThrow();

        // 2. Kiểm tra lượt xem và lượt tải không còn là 0
        assertTrue(updated.getDownloadCount() > 0, "Download count must be greater than 0");
        assertTrue(updated.getViewCount() > updated.getDownloadCount(), "View count must be greater than download count");

        // 3. Kiểm tra bình luận / đánh giá (reviews) được tạo thành công
        List<DocumentReview> reviews = reviewRepository
                .findByDocumentIdAndHiddenFalseAndCommentIsNotNullOrderByCreatedAtDesc(updated.getId());
        assertFalse(reviews.isEmpty(), "Document must have simulated reviews/comments");

        DocumentReview firstReview = reviews.get(0);
        assertTrue(firstReview.getRating() >= 4, "Rating should be 4 or 5 stars");
        assertNotNull(firstReview.getComment(), "Review comment must not be null");
        assertFalse(firstReview.getComment().isBlank(), "Review comment must not be blank");
        assertNotNull(firstReview.getUser(), "Reviewer must not be null");

        // 4. Điểm trung bình đánh giá
        double avgRating = reviewRepository.averageVisibleRating(updated.getId());
        assertTrue(avgRating >= 4.0, "Average visible rating should be at least 4.0 stars");
    }
}
