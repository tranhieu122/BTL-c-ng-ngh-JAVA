package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentReport;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentReportReason;
import com.hieu.edurepo.enums.DocumentReportStatus;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentReportRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
/**
 * Kiểm thử tích hợp chức năng phản hồi và đánh giá chất lượng AI (Document Feedback Integration Test).
 * Xác minh việc lưu trữ đánh giá Thích/Không thích kèm nhận xét của sinh viên vào cơ sở dữ liệu.
 */
class DocumentFeedbackIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentReviewRepository reviewRepository;
    @Autowired DocumentReportRepository reportRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @Test
    void authenticatedUserCanReviewPublishedDocument() throws Exception {
        User reader = saveUser("review-reader@example.test", RoleName.USER);
        Document document = saveDocument("Tài liệu công khai", DocumentStatus.PUBLISHED);

        mockMvc.perform(post("/document-reviews/{id}", document.getId())
                        .param("rating", "5").param("comment", "Rất hữu ích")
                        .param("helpful", "true").with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/repository/" + document.getId() + "#document-reviews"))
                .andExpect(flash().attribute("success", "Cảm ơn bạn đã đánh giá chất lượng tài liệu."));

        DocumentReview review = reviewRepository.findByDocumentIdAndUserId(document.getId(), reader.getId()).orElseThrow();
        assertEquals(5, review.getRating());
        assertTrue(review.isHelpful());
    }

    @Test
    void anonymousUserCannotReview() throws Exception {
        Document document = saveDocument("Cần đăng nhập", DocumentStatus.PUBLISHED);
        mockMvc.perform(post("/document-reviews/{id}", document.getId()).param("rating", "4").with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void unpublishedDocumentCannotBeReviewed() throws Exception {
        User reader = saveUser("draft-reader@example.test", RoleName.USER);
        Document document = saveDocument("Bản chưa công bố", DocumentStatus.APPROVED);
        mockMvc.perform(post("/document-reviews/{id}", document.getId()).param("rating", "4")
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Chỉ tài liệu đã công bố mới có thể được đánh giá."));
        assertEquals(0, reviewRepository.count());
    }

    @Test
    void duplicateReviewIsRejected() throws Exception {
        User reader = saveUser("duplicate-review@example.test", RoleName.USER);
        Document document = saveDocument("Một lượt đánh giá", DocumentStatus.PUBLISHED);
        saveReview(document, reader, 4, "Đánh giá đầu tiên");

        mockMvc.perform(post("/document-reviews/{id}", document.getId()).param("rating", "5")
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Bạn đã đánh giá tài liệu này. Hãy chỉnh sửa đánh giá hiện có."));
        assertEquals(1, reviewRepository.count());
    }

    @Test
    void ownerCanEditAndDeleteOwnReview() throws Exception {
        User reader = saveUser("review-owner@example.test", RoleName.USER);
        Document document = saveDocument("Sửa đánh giá", DocumentStatus.PUBLISHED);
        DocumentReview review = saveReview(document, reader, 2, "Bản cũ");

        mockMvc.perform(post("/document-reviews/{documentId}/{reviewId}", document.getId(), review.getId())
                        .param("rating", "5").param("comment", "Bản mới")
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(5, reviewRepository.findById(review.getId()).orElseThrow().getRating());

        mockMvc.perform(post("/document-reviews/{documentId}/{reviewId}/delete", document.getId(), review.getId())
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertFalse(reviewRepository.existsById(review.getId()));
    }

    @Test
    void userCannotEditOrDeleteAnotherUsersReview() throws Exception {
        User owner = saveUser("real-review-owner@example.test", RoleName.USER);
        User intruder = saveUser("review-intruder@example.test", RoleName.USER);
        Document document = saveDocument("Phân quyền đánh giá", DocumentStatus.PUBLISHED);
        DocumentReview review = saveReview(document, owner, 3, "Của người khác");

        mockMvc.perform(post("/document-reviews/{documentId}/{reviewId}", document.getId(), review.getId())
                        .param("rating", "1").with(user(CustomUserPrincipal.from(intruder))).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/document-reviews/{documentId}/{reviewId}/delete", document.getId(), review.getId())
                        .with(user(CustomUserPrincipal.from(intruder))).with(csrf()))
                .andExpect(status().isForbidden());
        assertEquals(3, reviewRepository.findById(review.getId()).orElseThrow().getRating());
    }

    @Test
    void reportCanBeSentButDuplicateReasonIsRejected() throws Exception {
        User reader = saveUser("reporter@example.test", RoleName.USER);
        Document document = saveDocument("Tài liệu cần báo cáo", DocumentStatus.PUBLISHED);

        mockMvc.perform(post("/document-reports/{id}", document.getId())
                        .param("reason", "FILE_UNOPENABLE").param("description", "Trình đọc báo lỗi")
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success",
                        "Đã gửi báo cáo để đội ngũ kiểm duyệt xem xét. Tài liệu chưa bị xóa."));
        mockMvc.perform(post("/document-reports/{id}", document.getId())
                        .param("reason", "FILE_UNOPENABLE")
                        .with(user(CustomUserPrincipal.from(reader))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Bạn đã báo cáo tài liệu này với cùng lý do."));
        assertEquals(1, reportRepository.count());
        assertEquals(DocumentStatus.PUBLISHED, documentRepository.findById(document.getId()).orElseThrow().getStatus());
    }

    @Test
    void reviewerCanViewAndProcessReportsAndHideReviews() throws Exception {
        User reader = saveUser("moderated-reader@example.test", RoleName.USER);
        User reviewer = saveUser("moderator@example.test", RoleName.REVIEWER);
        Document document = saveDocument("Điều tiết phản hồi", DocumentStatus.PUBLISHED);
        DocumentReport report = saveReport(document, reader);
        DocumentReview review = saveReview(document, reader, 1, "Nội dung không phù hợp");

        mockMvc.perform(get("/moderation/reports").with(user(CustomUserPrincipal.from(reviewer))))
                .andExpect(status().isOk()).andExpect(view().name("moderation/reports"));
        mockMvc.perform(post("/moderation/reports/{id}/status", report.getId())
                        .param("status", "RESOLVED").with(user(CustomUserPrincipal.from(reviewer))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/moderation/reviews/{id}/visibility", review.getId())
                        .param("hidden", "true").with(user(CustomUserPrincipal.from(reviewer))).with(csrf()))
                .andExpect(status().is3xxRedirection());

        DocumentReport updatedReport = reportRepository.findById(report.getId()).orElseThrow();
        assertEquals(DocumentReportStatus.RESOLVED, updatedReport.getStatus());
        assertEquals(reviewer.getId(), updatedReport.getHandledBy().getId());
        assertTrue(reviewRepository.findById(review.getId()).orElseThrow().isHidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotOpenReportManagement() throws Exception {
        mockMvc.perform(get("/moderation/reports")).andExpect(status().isForbidden());
    }

    @Test
    void threeOpenSeriousReportsShowAdministrativeWarning() throws Exception {
        User reviewer = saveUser("warning-reviewer@example.test", RoleName.REVIEWER);
        Document document = saveDocument("Tài liệu có cảnh báo", DocumentStatus.PUBLISHED);
        saveReport(document, saveUser("warning-one@example.test", RoleName.USER));
        saveReport(document, saveUser("warning-two@example.test", RoleName.USER));
        saveReport(document, saveUser("warning-three@example.test", RoleName.USER));

        mockMvc.perform(get("/moderation/reports").with(user(CustomUserPrincipal.from(reviewer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nhiều báo cáo nghiêm trọng chưa đóng")));
    }

    @Test
    void detailShowsCorrectAverageAndVisibleReviewCount() throws Exception {
        User first = saveUser("score-one@example.test", RoleName.USER);
        User second = saveUser("score-two@example.test", RoleName.USER);
        User hidden = saveUser("score-hidden@example.test", RoleName.USER);
        Document document = saveDocument("Điểm đánh giá", DocumentStatus.PUBLISHED);
        saveReview(document, first, 5, "Tốt");
        saveReview(document, second, 3, "Khá");
        DocumentReview hiddenReview = saveReview(document, hidden, 1, "Bị ẩn");
        hiddenReview.setHidden(true);
        reviewRepository.saveAndFlush(hiddenReview);

        var result = mockMvc.perform(get("/repository/{id}", document.getId()))
                .andExpect(status().isOk()).andReturn();
        var summary = (com.hieu.edurepo.dto.DocumentReviewSummary) Objects.requireNonNull(result.getModelAndView(),
                "Expected the document-detail request to render a model").getModel().get("reviewSummary");
        assertEquals(4.0, summary.averageRating(), 0.001);
        assertEquals(2, summary.reviewCount());
    }

    private User saveUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(email);
        user.setFullName("Người dùng " + email);
        user.setEmail(email);
        user.setPassword("password123");
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }

    private Document saveDocument(String title, DocumentStatus status) {
        Document document = new Document();
        document.setTitle(title);
        document.setStatus(status);
        document.setFileName("document.pdf");
        document.setFilePath("document.pdf");
        document.setFileType("application/pdf");
        return documentRepository.saveAndFlush(document);
    }

    private DocumentReview saveReview(Document document, User user, int rating, String comment) {
        DocumentReview review = new DocumentReview();
        review.setDocument(document);
        review.setUser(user);
        review.setRating(rating);
        review.setComment(comment);
        return reviewRepository.saveAndFlush(review);
    }

    private DocumentReport saveReport(Document document, User user) {
        DocumentReport report = new DocumentReport();
        report.setDocument(document);
        report.setReporter(user);
        report.setReason(DocumentReportReason.COPYRIGHT_CONCERN);
        return reportRepository.saveAndFlush(report);
    }
}
