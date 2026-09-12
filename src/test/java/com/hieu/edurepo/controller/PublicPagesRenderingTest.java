package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class PublicPagesRenderingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void publicPagesRenderWithPublishedDocument() throws Exception {
        Document document = new Document();
        document.setTitle("Giáo trình kiểm thử giao diện");
        document.setDescription("Tài liệu mẫu dùng để xác nhận các template Thymeleaf được render thành công.");
        document.setAuthorName("EduRepo QA");
        document.setFileName("giao-trinh-kiem-thu.pdf");
        document.setFileType("application/pdf");
        document.setFileSize(1_572_864L);
        document.setStatus(DocumentStatus.PUBLISHED);
        document.setPublishedAt(LocalDateTime.now());
        Document savedDocument = documentRepository.save(document);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/home"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Tìm đúng học liệu")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"keyword\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Tài liệu công khai")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Giáo trình kiểm thử giao diện")));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/login"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-login-form")));

        mockMvc.perform(get("/login").param("expired", ""))
                .andExpect(status().isOk())
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("auth-alert-copy")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Phiên đăng nhập đã hết hiệu lực")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"_csrf\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-password-toggle")));

        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/forgot-password"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/repository"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/repository"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Giáo trình kiểm thử giao diện")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-document-card")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-confirm-dialog")));

        mockMvc.perform(get("/repository/{id}", savedDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("public/document-detail"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Giáo trình kiểm thử giao diện")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-share-link")));

        mockMvc.perform(get("/repository/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("error-shell")));

        mockMvc.perform(get("/access-denied"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/access-denied"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("error-shell")));
    }

    @Test
    void frontendEntryPointsAndModulesAreServed() throws Exception {
        String[] assets = {
                "/css/style.css",
                "/css/foundation.css",
                "/css/app-shell.css",
                "/css/workspace.css",
                "/css/responsive.css",
                "/css/components/header.css",
                "/css/components/footer.css",
                "/css/components/feedback.css",
                "/css/pages/repository.css",
                "/css/pages/document-detail.css",
                "/css/pages/login.css",
                "/css/pages/error-pages.css",
                "/js/main.js",
                "/js/modules/core.js",
                "/js/modules/navigation.js",
                "/js/modules/catalog.js",
                "/js/modules/auth.js",
                "/js/modules/document-actions.js",
                "/js/modules/forms.js"
        };

        for (String asset : assets) {
            mockMvc.perform(get(asset))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/css/style.css"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("@import url(\"./foundation.css\")")));

        mockMvc.perform(get("/js/main.js"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("./modules/core.js")));
    }

    @Test
    @WithMockUser(roles = {"ADMIN", "REVIEWER", "SUBMITTER"})
    void workspacePagesRenderForAdmin() throws Exception {
        String[][] pages = {
                {"/admin/dashboard", "admin/dashboard"},
                {"/admin/users", "admin/users"},
                {"/admin/users/new", "admin/user-form"},
                {"/admin/categories", "admin/categories"},
                {"/admin/organization", "admin/organization"},
                {"/documents/new", "documents/form"},
                {"/reviews/pending", "reviews/pending"}
        };

        for (String[] page : pages) {
            mockMvc.perform(get(page[0]))
                    .andExpect(status().isOk())
                    .andExpect(view().name(page[1]))
                    .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("workspace-content")));
        }

        mockMvc.perform(get("/admin/users"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Chưa có người dùng")));

        mockMvc.perform(get("/admin/categories"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Chưa có danh mục")));

        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElseThrow();
        User adminUser = new User();
        adminUser.setUsername("ui-admin");
        adminUser.setFullName("Quản trị giao diện");
        adminUser.setEmail("ui-admin@example.test");
        adminUser.setPassword("encoded-test-password");
        adminUser.setRoles(Set.of(adminRole));
        userRepository.save(adminUser);

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("Quản trị giao diện")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString(">ADMIN</span>")));

        mockMvc.perform(get("/admin/users/new"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"fullName\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"roles\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"enabled\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/documents/new"))
                .andExpect(status().isOk())
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("enctype=\"multipart/form-data\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"title\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"categoryId\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"facultyId\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"file\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"_csrf\"")));

        Document reviewDocument = new Document();
        reviewDocument.setTitle("Tài liệu cần kiểm duyệt giao diện");
        reviewDocument.setFileName("review.pdf");
        reviewDocument.setFileType("application/pdf");
        reviewDocument.setStatus(DocumentStatus.SUBMITTED);
        Document savedReviewDocument = documentRepository.save(reviewDocument);

        mockMvc.perform(get("/reviews/{id}", savedReviewDocument.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("reviews/detail"))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("data-review-action")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("value=\"APPROVED\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("value=\"REVISION_REQUESTED\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("value=\"REJECTED\"")))
                .andExpect(content().string((org.hamcrest.Matcher<? super String>) containsString("name=\"_csrf\"")));
    }
}
