package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.AuditLog;
import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.AuditLogRepository;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.repository.DepartmentRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.FacultyRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuditLogRepository auditLogs;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DocumentRepository documents;
    @Autowired CategoryRepository categories;
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @Autowired com.hieu.edurepo.service.FileStorageService files;

    @BeforeTransaction
    @AfterTransaction
    void clearCommittedAuditLogs() {
        auditLogs.deleteAll();
    }

    @Test
    void adminCanViewAuditPageAndRegularUserCannot() throws Exception {
        User admin = saveUser("audit-admin@example.test", RoleName.ADMIN);
        User user = saveUser("audit-user@example.test", RoleName.USER);
        AuditLog detailLog = saveLog(admin, AuditAction.LOGIN_SUCCESS, "Đăng nhập kiểm thử", LocalDateTime.now());

        mockMvc.perform(get("/admin/audit-logs").with(user(CustomUserPrincipal.from(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/audit-logs"));
        mockMvc.perform(get("/admin/audit-logs").with(user(CustomUserPrincipal.from(user))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/audit-logs/{id}", detailLog.getId())
                        .with(user(CustomUserPrincipal.from(admin))))
                .andExpect(status().isOk()).andExpect(view().name("admin/audit-log-detail"));
    }

    @Test
    void submittingDocumentCreatesAuditLog() throws Exception {
        User submitter = saveUser("audit-submitter@example.test", RoleName.SUBMITTER);
        Document document = saveDocument("Tài liệu cần gửi", DocumentStatus.DRAFT, submitter);

        mockMvc.perform(post("/documents/{id}/submit", document.getId())
                        .with(user(CustomUserPrincipal.from(submitter))).with(csrf()))
                .andExpect(status().is3xxRedirection());

        var log = auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_SUBMITTED).orElseThrow();
        assertEquals(document.getId(), log.getTargetId());
        assertEquals(submitter.getId(), log.getActorId());
        assertEquals(AuditResult.SUCCESS, log.getResult());
    }

    @Test
    void uploadSubmitAndDownloadArePersistedAsAuditLogs() throws Exception {
        User submitter = saveUser("audit-file-flow@example.test", RoleName.SUBMITTER);
        var category = new com.hieu.edurepo.entity.Category();
        category.setName("Audit category " + java.util.UUID.randomUUID());
        category.setActive(true);
        category = categories.saveAndFlush(category);
        var faculty = new com.hieu.edurepo.entity.Faculty();
        faculty.setName("Audit faculty " + java.util.UUID.randomUUID());
        faculty.setActive(true);
        faculty = faculties.saveAndFlush(faculty);
        var department = new com.hieu.edurepo.entity.Department();
        department.setName("Audit department " + java.util.UUID.randomUUID());
        department.setFaculty(faculty);
        department.setActive(true);
        department = departments.saveAndFlush(department);
        String title = "Tài liệu audit " + java.util.UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "audit-flow.pdf", "application/pdf",
                "%PDF-1.4\nAudit integration".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        mockMvc.perform(multipart("/documents").file(file)
                        .param("title", title).param("description", "Kiểm tra lưu audit xuống database")
                        .param("categoryId", category.getId().toString())
                        .param("facultyId", faculty.getId().toString())
                        .param("departmentId", department.getId().toString())
                        .param("learningResourceType", "OTHER").param("educationLevel", "ALL_LEVELS")
                        .param("intent", "submit")
                        .with(user(CustomUserPrincipal.from(submitter))).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Document document = documents.findAll().stream().filter(item -> title.equals(item.getTitle())).findFirst().orElseThrow();
        try {
            assertEquals(document.getId(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_UPLOADED)
                    .orElseThrow().getTargetId());
            assertEquals(document.getId(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_SUBMITTED)
                    .orElseThrow().getTargetId());

            document.setStatus(DocumentStatus.PUBLISHED);
            documents.saveAndFlush(document);
            mockMvc.perform(get("/download/{id}", document.getId())
                            .with(user(CustomUserPrincipal.from(submitter))))
                    .andExpect(status().isOk());
            assertEquals(document.getId(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_DOWNLOADED)
                    .orElseThrow().getTargetId());
        } finally {
            if (document.getFilePath() != null) files.delete(document.getFilePath());
        }
    }

    @Test
    void reviewerApprovalAndRejectionCreateAuditLogs() throws Exception {
        User submitter = saveUser("audit-review-owner@example.test", RoleName.SUBMITTER);
        User reviewer = saveUser("audit-reviewer@example.test", RoleName.REVIEWER);
        Document approved = saveDocument("Tài liệu được duyệt", DocumentStatus.SUBMITTED, submitter);
        Document rejected = saveDocument("Tài liệu bị từ chối", DocumentStatus.SUBMITTED, submitter);

        mockMvc.perform(get("/reviews/pending").with(user(CustomUserPrincipal.from(reviewer))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/reviews/{id}", approved.getId()).with(user(CustomUserPrincipal.from(reviewer))))
                .andExpect(status().isOk());

        review(approved, reviewer, "APPROVED");
        review(rejected, reviewer, "REJECTED");

        assertEquals(approved.getId(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_APPROVED)
                .orElseThrow().getTargetId());
        assertEquals(rejected.getId(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.DOCUMENT_REJECTED)
                .orElseThrow().getTargetId());
        assertTrue(auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.REVIEW_QUEUE_VIEWED).isPresent());
        assertEquals(approved.getTitle(), auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.REVIEW_DOCUMENT_VIEWED)
                .orElseThrow().getTargetName());
    }

    @Test
    void guestViewsAreLoggedButUnauthorizedReviewerActionIsNot() throws Exception {
        User regularUser = saveUser("audit-regular@example.test", RoleName.USER);

        mockMvc.perform(get("/")) .andExpect(status().isOk());

        AuditLog guestView = auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.HOME_VIEWED).orElseThrow();
        assertEquals("GUEST", guestView.getActorRoles());
        assertEquals("Trang chủ", guestView.getTargetName());

        mockMvc.perform(get("/reviews/pending").with(user(CustomUserPrincipal.from(regularUser))))
                .andExpect(status().isForbidden());
        assertTrue(auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.REVIEW_QUEUE_VIEWED).isEmpty());
    }

    @Test
    void userBookmarkActionsAreLoggedWithDocumentTarget() throws Exception {
        User user = saveUser("audit-bookmark@example.test", RoleName.USER);
        Document document = saveDocument("Tài liệu bookmark", DocumentStatus.PUBLISHED, user);

        mockMvc.perform(post("/library/bookmarks/{id}", document.getId())
                        .with(user(CustomUserPrincipal.from(user))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        AuditLog saved = auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.BOOKMARK_SAVED).orElseThrow();
        assertEquals(document.getId(), saved.getTargetId());
        assertEquals(document.getTitle(), saved.getTargetName());

        mockMvc.perform(post("/library/bookmarks/{id}", document.getId())
                        .with(user(CustomUserPrincipal.from(user))).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertTrue(auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.BOOKMARK_REMOVED).isPresent());
    }

    @Test
    void filtersByActionActorAndDate() throws Exception {
        User admin = saveUser("audit-filter-admin@example.test", RoleName.ADMIN);
        saveLog(admin, AuditAction.DOCUMENT_CREATED, "Tạo tài liệu phù hợp", LocalDateTime.now());
        saveLog(admin, AuditAction.USER_UPDATED, "Bản ghi không phù hợp", LocalDateTime.now().minusDays(10));
        saveGuestLog(AuditAction.DOCUMENT_CREATED, "Tạo tài liệu không đúng vai trò", LocalDateTime.now());

        var result = mockMvc.perform(get("/admin/audit-logs")
                        .param("action", "DOCUMENT_CREATED")
                        .param("actorId", admin.getId().toString())
                        .param("actorRole", "ADMIN")
                        .param("fromDate", java.time.LocalDate.now().minusDays(1).toString())
                        .param("toDate", java.time.LocalDate.now().toString())
                        .with(user(CustomUserPrincipal.from(admin))))
                .andExpect(status().isOk()).andReturn();

        @SuppressWarnings("unchecked")
        Page<AuditLog> page = (Page<AuditLog>) Objects.requireNonNull(result.getModelAndView(),
                "Expected the audit-log request to render a model").getModel().get("logs");
        assertEquals(1, page.getTotalElements());
        assertEquals(AuditAction.DOCUMENT_CREATED, page.getContent().get(0).getAction());
    }

    private void review(Document document, User reviewer, String action) throws Exception {
        mockMvc.perform(post("/reviews/{id}", document.getId()).param("action", action)
                        .param("comment", "Kiểm thử nhật kí").param("contentQualityScore", "4")
                        .param("teachingEffectivenessScore", "4").param("easeOfUseScore", "4")
                        .with(user(CustomUserPrincipal.from(reviewer))).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private AuditLog saveLog(User actor, AuditAction action, String description, LocalDateTime time) {
        AuditLog log = new AuditLog();
        log.setOccurredAt(time);
        log.setActorId(actor.getId());
        log.setActorName(actor.getFullName());
        log.setActorIdentifier(actor.getEmail());
        log.setActorRoles("ADMIN");
        log.setAction(action);
        log.setTargetType(AuditTargetType.SYSTEM);
        log.setDescription(description);
        log.setResult(AuditResult.SUCCESS);
        return auditLogs.saveAndFlush(log);
    }

    private AuditLog saveGuestLog(AuditAction action, String description, LocalDateTime time) {
        AuditLog log = new AuditLog();
        log.setOccurredAt(time);
        log.setActorName("Khách");
        log.setActorRoles("GUEST");
        log.setAction(action);
        log.setTargetType(AuditTargetType.SYSTEM);
        log.setDescription(description);
        log.setResult(AuditResult.SUCCESS);
        return auditLogs.saveAndFlush(log);
    }

    private Document saveDocument(String title, DocumentStatus status, User owner) {
        Document document = new Document();
        document.setTitle(title);
        document.setStatus(status);
        document.setCreatedBy(owner);
        document.setFileName("audit.pdf");
        document.setFilePath("audit.pdf");
        document.setFileType("application/pdf");
        return documents.saveAndFlush(document);
    }

    private User saveUser(String email, RoleName roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(email);
        user.setFullName("Người dùng audit");
        user.setEmail(email);
        user.setPassword("Password123");
        user.setRoles(Set.of(role));
        return users.saveAndFlush(user);
    }
}
