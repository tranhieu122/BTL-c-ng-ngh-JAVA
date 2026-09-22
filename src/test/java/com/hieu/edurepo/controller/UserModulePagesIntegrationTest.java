package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.DocumentReview;
import com.hieu.edurepo.entity.SubmitterRequest;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.DocumentReviewRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.SubmitterRequestRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.LibraryService;
import com.hieu.edurepo.service.UserActivityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
/**
 * Kiểm thử tích hợp giao diện trang quản lý người dùng (User Module Pages Integration Test).
 * Xác minh việc hiển thị danh sách thành viên, cột phân vai trò và các nút chức năng khóa tài khoản.
 */
class UserModulePagesIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private DocumentRepository documents;
    @Autowired private CategoryRepository categories;
    @Autowired private DocumentReviewRepository reviews;
    @Autowired private SubmitterRequestRepository requests;
    @Autowired private UserActivityService activities;
    @Autowired private LibraryService library;

    @Test
    void libraryRendersHelpfulEmptyStatesForANewReader() throws Exception {
        User reader = saveUser("empty-library", RoleName.USER);

        mockMvc.perform(get("/library").with(user(CustomUserPrincipal.from(reader))))
                .andExpect(status().isOk()).andExpect(view().name("library/index"))
                .andExpect(content().string(containsString("Kệ tài liệu đang chờ bạn")))
                .andExpect(content().string(containsString("Chưa có bộ sưu tập")))
                .andExpect(content().string(containsString("Tìm học liệu đầu tiên")));
    }

    @Test
    void userAndAdminPagesRenderPopulatedActivity() throws Exception {
        User reader = saveUser("reader", RoleName.USER);
        User admin = saveUser("admin", RoleName.ADMIN);
        Category category = new Category();
        category.setName("Lập trình " + UUID.randomUUID());
        category = categories.save(category);
        Document document = new Document();
        document.setTitle("Học liệu Java cho USER");
        document.setDescription("Dữ liệu kiểm tra trang hoạt động cá nhân");
        document.setCategory(category);
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setPublishedAt(LocalDateTime.now());
        document = documents.save(document);

        activities.recordView(reader.getId(), document.getId());
        activities.recordDownload(reader.getId(), document.getId());
        library.toggleBookmark(reader, document.getId());
        library.createCollection(reader, "Lộ trình Java", "Tài liệu dành cho kế hoạch ôn tập Java");
        DocumentReview review = new DocumentReview();
        review.setDocument(document);
        review.setUser(reader);
        review.setRating(5);
        review.setComment("Tài liệu hữu ích");
        reviews.save(review);
        SubmitterRequest request = new SubmitterRequest();
        request.setRequester(reader);
        request.setReason("Tôi muốn đóng góp giáo trình Java");
        requests.save(request);

        var readerPrincipal = user(CustomUserPrincipal.from(reader));
        mockMvc.perform(get("/library").with(readerPrincipal))
                .andExpect(status().isOk()).andExpect(view().name("library/index"))
                .andExpect(content().string(containsString("library-dashboard")))
                .andExpect(content().string(containsString("Học liệu Java cho USER")))
                .andExpect(content().string(containsString("Lộ trình Java")))
                .andExpect(content().string(containsString("Tạo bộ sưu tập mới")));
        mockMvc.perform(get("/library/recently-viewed").with(readerPrincipal))
                .andExpect(status().isOk()).andExpect(view().name("library/activity"))
                .andExpect(content().string(containsString("Học liệu Java cho USER")))
                .andExpect(content().string(containsString(category.getName())));
        mockMvc.perform(get("/library/download-history").with(readerPrincipal))
                .andExpect(status().isOk()).andExpect(content().string(containsString("1 lượt tải")))
                .andExpect(content().string(containsString(category.getName())));
        mockMvc.perform(get("/library/my-reviews").with(readerPrincipal))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Tài liệu hữu ích")));
        mockMvc.perform(get("/submitter-request").with(readerPrincipal))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đang chờ duyệt")));

        mockMvc.perform(get("/admin/submitter-requests").with(user(CustomUserPrincipal.from(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/submitter-requests"))
                .andExpect(content().string(containsString("Tôi muốn đóng góp giáo trình Java")));
    }

    private User saveUser(String prefix, RoleName roleName) {
        String token = UUID.randomUUID().toString();
        User account = new User();
        account.setUsername(prefix + "-" + token);
        account.setFullName(prefix.equals("admin") ? "Quản trị viên" : "Người đọc EduRepo");
        account.setEmail(prefix + "-" + token + "@example.test");
        account.setPassword("encoded-test-password");
        account.setRoles(Set.of(roles.findByName(roleName).orElseThrow()));
        return users.save(account);
    }
}
