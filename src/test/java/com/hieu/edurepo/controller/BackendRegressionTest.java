package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BackendRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void negativeRepositoryPageFallsBackToFirstPage() throws Exception {
        mockMvc.perform(get("/repository").param("page", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/repository"));
    }

    @SuppressWarnings("null")
    @Test
    @WithMockUser(roles = "REVIEWER")
    void approvedDocumentRemainsInReviewQueueForPublication() throws Exception {
        Document document = saveDocument("Tài liệu đã duyệt chờ công bố", DocumentStatus.APPROVED, null);

        mockMvc.perform(get("/reviews/pending"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(document.getTitle())));
    }

    @Test
    @WithMockUser(roles = "REVIEWER")
    void reviewerCannotOpenDraftByDirectUrl() throws Exception {
        Document document = saveDocument("Bản nháp riêng tư", DocumentStatus.DRAFT, null);

        mockMvc.perform(get("/reviews/{id}", document.getId()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
    }

    @Test
    @WithMockUser(roles = "SUBMITTER")
    void submitterCannotUseReviewerDownloadEndpoint() throws Exception {
        mockMvc.perform(get("/reviews/{id}/download", 1L))
                .andExpect(status().isForbidden());
    }

    @SuppressWarnings("null")
    @Test
    void ownerViolationReturnsForbiddenInsteadOfBadRequest() throws Exception {
        User owner = saveUser("owner@example.test", RoleName.SUBMITTER);
        User intruder = saveUser("intruder@example.test", RoleName.SUBMITTER);
        Document document = saveDocument("Tài liệu của người khác", DocumentStatus.DRAFT, owner);

        mockMvc.perform(get("/documents/{id}", document.getId())
                        .with(user(CustomUserPrincipal.from(intruder))))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/403"));
    }

    @SuppressWarnings("null")
    @Test
    void adminCannotDeleteOwnAccount() throws Exception {
        User admin = saveUser("self-admin@example.test", RoleName.ADMIN);

        mockMvc.perform(post("/admin/users/{id}/delete", admin.getId())
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"));

        assertTrue(userRepository.existsById(admin.getId()));
    }

    @Test
    void adminCannotDisableOwnAccount() throws Exception {
        User admin = saveUser("protected-admin@example.test", RoleName.ADMIN);

        mockMvc.perform(post("/admin/users/save")
                        .param("id", admin.getId().toString())
                        .param("fullName", admin.getFullName())
                        .param("email", admin.getEmail())
                        .param("roles", "ADMIN")
                        .param("_enabled", "on")
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().hasErrors());

        assertTrue(userRepository.findById(admin.getId()).orElseThrow().isEnabled());
    }

    @Test
    void deletingReferencedUserReturnsFriendlyError() throws Exception {
        User admin = saveUser("deletion-admin@example.test", RoleName.ADMIN);
        User submitter = saveUser("referenced-submit@example.test", RoleName.SUBMITTER);
        saveDocument("Tài liệu giữ liên kết người nộp", DocumentStatus.DRAFT, submitter);

        mockMvc.perform(post("/admin/users/{id}/delete", submitter.getId())
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users"))
                .andExpect(flash().attribute("error",
                        "Không thể xóa người dùng đã có tài liệu hoặc lịch sử kiểm duyệt"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void blankCategoryIsRejectedByServerValidation() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .param("name", "   ")
                        .param("description", "Không hợp lệ")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories"))
                .andExpect(model().attributeHasFieldErrors("category", "name"));

        assertTrue(categoryRepository.findAll().stream()
                .noneMatch(category -> category.getName() == null || category.getName().isBlank()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateCategoryReturnsFieldErrorInsteadOfDatabaseFailure() throws Exception {
        Category category = new Category();
        category.setName("Công nghệ thông tin");
        categoryRepository.saveAndFlush(category);

        mockMvc.perform(post("/admin/categories")
                        .param("name", "  CÔNG NGHỆ THÔNG TIN  ")
                        .param("description", "Tên bị trùng")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/categories"))
                .andExpect(model().attributeHasFieldErrors("category", "name"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateAdminEmailReturnsFieldErrorInsteadOfServerError() throws Exception {
        saveUser("duplicate@example.test", RoleName.USER);

        mockMvc.perform(post("/admin/users/save")
                        .param("fullName", "Người dùng trùng")
                        .param("email", "duplicate@example.test")
                        .param("password", "password123")
                        .param("enabled", "true")
                        .param("roles", "USER")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().attributeHasFieldErrors("userForm", "email"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreateRequiresAtLeastOneRoleAndSafePassword() throws Exception {
        mockMvc.perform(post("/admin/users/save")
                        .param("fullName", "Người dùng thiếu quyền")
                        .param("email", "missing-role@example.test")
                        .param("password", "short")
                        .param("enabled", "true")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/user-form"))
                .andExpect(model().attributeHasFieldErrors("userForm", "roles", "password"));
    }

    @Test
    void loginNormalizesEmailCaseAndWhitespace() throws Exception {
        Role role = roleRepository.findByName(RoleName.USER).orElseThrow();
        User user = new User();
        user.setUsername("login@example.test");
        user.setFullName("Người đăng nhập");
        user.setEmail("login@example.test");
        user.setPassword(passwordEncoder.encode("password123"));
        user.setRoles(Set.of(role));
        userRepository.saveAndFlush(user);

        mockMvc.perform(post("/login")
                        .param("email", "  LOGIN@EXAMPLE.TEST  ")
                        .param("password", "password123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void adminCreatedUsersCanShareEmailLocalPart() {
        Role role = roleRepository.findByName(RoleName.USER).orElseThrow();
        User first = newUser("shared@first.example", role);
        User second = newUser("shared@second.example", role);
        first.setUsername(null);
        second.setUsername(null);

        userService.save(first, true);
        userRepository.flush();

        assertDoesNotThrow(() -> {
            userService.save(second, true);
            userRepository.flush();
        });
    }

    private Document saveDocument(String title, DocumentStatus status, User owner) {
        Document document = new Document();
        document.setTitle(title);
        document.setStatus(status);
        document.setCreatedBy(owner);
        document.setFileName("document.pdf");
        document.setFilePath("document.pdf");
        document.setFileType("application/pdf");
        return documentRepository.saveAndFlush(document);
    }

    private User saveUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = newUser(email, role);
        user.setPassword(passwordEncoder.encode("password123"));
        return userRepository.saveAndFlush(user);
    }

    private User newUser(String email, Role role) {
        User user = new User();
        user.setUsername(email);
        user.setFullName("Người dùng " + email);
        user.setEmail(email);
        user.setPassword("password123");
        user.setRoles(Set.of(role));
        return user;
    }
}
