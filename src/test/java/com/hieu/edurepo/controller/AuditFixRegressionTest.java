package com.hieu.edurepo.controller;

import com.hieu.edurepo.config.DataInitializer;
import com.hieu.edurepo.entity.*;
import com.hieu.edurepo.enums.*;
import com.hieu.edurepo.exception.InvalidStatusException;
import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.Objects;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:audit_fixes;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.upload.dir=target/fix-test-uploads"
})
@AutoConfigureMockMvc
class AuditFixRegressionTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired UserService userService;
    @Autowired PasswordEncoder encoder;
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @Autowired CategoryRepository categories;
    @Autowired DocumentRepository documents;
    @Autowired DocumentService documentService;
    @Autowired ReviewService reviewService;
    @Autowired DataInitializer initializer;
    @Autowired LibraryService library;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void disabledAccountCannotReuseItsLoggedInSession() throws Exception {
        User account = account(RoleName.REVIEWER);
        MockHttpSession session = login(account);
        account.setEnabled(false);
        userService.save(account, false);
        mvc.perform(get("/reviews/pending").session(session)).andExpect(redirectedUrl("/login?expired"));
        assertTrue(session.isInvalid());
    }

    @Test
    void removedAdminRoleCannotReuseItsLoggedInSession() throws Exception {
        User account = account(RoleName.ADMIN);
        MockHttpSession session = login(account);
        account.setRoles(Set.of(roles.findByName(RoleName.USER).orElseThrow()));
        userService.save(account, false);
        mvc.perform(get("/admin/users").session(session)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void changedPasswordAndDeletedAccountInvalidateSessions() throws Exception {
        User account = account(RoleName.USER);
        MockHttpSession oldSession = login(account);
        account.setPassword("NewPassword123");
        userService.save(account, true);
        mvc.perform(get("/library").session(oldSession)).andExpect(redirectedUrl("/login?expired"));

        User deleted = account(RoleName.USER);
        MockHttpSession deletedSession = login(deleted);
        userService.deleteById(deleted.getId());
        mvc.perform(get("/library").session(deletedSession)).andExpect(redirectedUrl("/login?expired"));
    }

    @Test
    void oldEmailCanBeRegisteredAfterAnEmailChange() {
        String previous = email();
        User account = userService.register("First user", previous, "Password123");
        account.setEmail(email());
        userService.save(account, false);
        User replacement = userService.register("Second user", previous, "Password123");
        assertEquals(previous, replacement.getEmail());
        assertNotEquals(account.getId(), replacement.getId());
    }

    @Test
    void oversizedAsciiAndUnicodePasswordsReturnFieldErrors() throws Exception {
        for (String password : new String[]{"a".repeat(73), "ắ".repeat(25)}) {
            mvc.perform(post("/register").with(csrf()).param("fullName", "Long Password")
                            .param("email", email()).param("password", password).param("confirmPassword", password))
                    .andExpect(status().isOk()).andExpect(view().name("auth/register"))
                    .andExpect(model().attributeHasFieldErrors("registerForm", "password"));
            mvc.perform(post("/admin/users/save").with(user("admin").roles("ADMIN")).with(csrf())
                            .param("fullName", "Long Password").param("email", email()).param("password", password)
                            .param("roles", "USER").param("enabled", "true"))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("userForm", "password"));
        }
        assertTrue(com.hieu.edurepo.util.PasswordPolicy.isValid("ắ".repeat(24)));
    }

    @Test
    void recoveryRequestsCreateOtpAndCloseAdminQueueAfterPasswordChange() throws Exception {
        User account = account(RoleName.USER);
        mvc.perform(post("/forgot-password").param("email", account.getEmail()).with(csrf()))
                .andExpect(redirectedUrl("/forgot-password/verify")).andExpect(flash().attribute("resetRequested", true));
        var requestedAt = users.findById(account.getId()).orElseThrow().getPasswordResetRequestedAt();
        assertNotNull(requestedAt);
        userService.requestPasswordReset(account.getEmail());
        assertEquals(requestedAt, users.findById(account.getId()).orElseThrow().getPasswordResetRequestedAt());
        mvc.perform(post("/forgot-password").param("email", email()).with(csrf()))
                .andExpect(redirectedUrl("/forgot-password/verify")).andExpect(flash().attribute("resetRequested", true));
        mvc.perform(get("/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đặt mật khẩu mới")));
        mvc.perform(post("/admin/users/save").with(user("admin").roles("ADMIN")).with(csrf())
                        .param("id", account.getId().toString()).param("fullName", account.getFullName())
                        .param("email", account.getEmail()).param("password", "RecoveredPassword123")
                        .param("roles", "USER").param("enabled", "true"))
                .andExpect(redirectedUrl("/admin/users"));
        User recovered = users.findById(account.getId()).orElseThrow();
        assertNull(recovered.getPasswordResetRequestedAt());
        assertTrue(encoder.matches("RecoveredPassword123", recovered.getPassword()));
    }

    @Test
    void organizationEditsSurviveInitializerReplay() {
        Department department = departments.findAll().get(0);
        Faculty original = department.getFaculty();
        Faculty destination = new Faculty();
        destination.setName("Audit faculty " + UUID.randomUUID());
        destination = faculties.saveAndFlush(destination);
        department.setFaculty(destination);
        departments.saveAndFlush(department);
        original.setName("Renamed " + UUID.randomUUID());
        faculties.saveAndFlush(original);
        long facultyCount = faculties.count();
        long departmentCount = departments.count();
        assertDoesNotThrow(() -> initializer.run());
        assertEquals(facultyCount, faculties.count());
        assertEquals(departmentCount, departments.count());
        assertEquals(destination.getId(), departments.findById(department.getId()).orElseThrow().getFaculty().getId());
    }

    @Test
    void serverDraftCanBeEditedWithoutLosingLicenseAndThenSubmitted() throws Exception {
        User owner = account(RoleName.SUBMITTER);
        Category category = category();
        Department department = departments.findAll().get(0);
        mvc.perform(multipart("/documents").file(pdf()).with(user(CustomUserPrincipal.from(owner))).with(csrf())
                        .param("intent", "draft").param("title", "Server draft").param("description", "Draft description")
                        .param("categoryId", category.getId().toString()).param("facultyId", department.getFaculty().getId().toString())
                        .param("departmentId", department.getId().toString()).param("licenseType", "CC_BY"))
                .andExpect(redirectedUrl("/documents")).andExpect(flash().attribute("clearSubmissionDraft", true));
        Document draft = documents.findByCreatedByIdOrderByCreatedAtDesc(owner.getId()).get(0);
        assertEquals(DocumentStatus.DRAFT, draft.getStatus());
        assertEquals(1, documentService.findVersions(draft.getId()).size());
        mvc.perform(get("/repository/" + draft.getId())).andExpect(status().isNotFound());
        mvc.perform(post("/documents/" + draft.getId()).with(user(CustomUserPrincipal.from(owner))).with(csrf())
                        .param("title", "Updated title").param("description", "Updated description")
                        .param("categoryId", category.getId().toString()).param("facultyId", department.getFaculty().getId().toString())
                        .param("departmentId", department.getId().toString()))
                .andExpect(redirectedUrl("/documents/" + draft.getId()));
        assertEquals(LicenseType.CC_BY, documents.findById(draft.getId()).orElseThrow().getLicenseType());
        mvc.perform(post("/documents/" + draft.getId() + "/submit").with(user(CustomUserPrincipal.from(owner))).with(csrf()))
                .andExpect(redirectedUrl("/documents"));
        assertEquals(DocumentStatus.SUBMITTED, documents.findById(draft.getId()).orElseThrow().getStatus());
    }

    @Test
    void missingDownloadDoesNotIncreaseUsageCount() throws Exception {
        Document document = document(account(RoleName.SUBMITTER), DocumentStatus.PUBLISHED);
        document.setFilePath("missing-" + UUID.randomUUID() + ".pdf");
        documents.saveAndFlush(document);
        mvc.perform(get("/download/" + document.getId())).andExpect(status().isNotFound());
        assertEquals(0, documents.findById(document.getId()).orElseThrow().getDownloadCount());
    }

    @Test
    void approvedReviewPageOffersPublishingOnlyToAdmin() throws Exception {
        Document document = document(account(RoleName.SUBMITTER), DocumentStatus.APPROVED);
        mvc.perform(get("/reviews/" + document.getId()).with(user("reviewer").roles("REVIEWER")))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("/publish"))))
                .andExpect(content().string(not(containsString("value=\"REJECTED\""))))
                .andExpect(content().string(not(containsString("value=\"APPROVED\""))));
        mvc.perform(get("/reviews/" + document.getId()).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("/publish")));
    }

    @Test
    void competingReviewDecisionsCannotBothCommit() throws Exception {
        Document document = document(account(RoleName.SUBMITTER), DocumentStatus.UNDER_REVIEW);
        User firstReviewer = account(RoleName.REVIEWER);
        User secondReviewer = account(RoleName.REVIEWER);
        CountDownLatch decisionSaved = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                reviewService.review(document.getId(), ReviewAction.APPROVED, "Approve", firstReviewer);
                decisionSaved.countDown();
                try { if (!allowCommit.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for test"); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
            }));
            assertTrue(decisionSaved.await(10, TimeUnit.SECONDS));
            var second = executor.submit(() -> {
                secondStarted.countDown();
                return reviewService.review(document.getId(), ReviewAction.REJECTED, "Reject", secondReviewer);
            });
            try {
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            } finally { allowCommit.countDown(); }
            first.get(10, TimeUnit.SECONDS);
            ExecutionException conflict = assertThrows(ExecutionException.class, () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(InvalidStatusException.class, conflict.getCause());
            assertEquals(DocumentStatus.APPROVED, documents.findById(document.getId()).orElseThrow().getStatus());
            assertEquals(1, reviewService.history(document.getId()).size());
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void categoriesCanBeEditedAndReactivated() throws Exception {
        Category category = category();
        category.setActive(false);
        categories.saveAndFlush(category);
        mvc.perform(get("/admin/categories/" + category.getId() + "/edit").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Lưu thay đổi")));
        mvc.perform(post("/admin/categories/" + category.getId()).with(user("admin").roles("ADMIN")).with(csrf())
                        .param("name", "Changed " + UUID.randomUUID()).param("description", "Updated"))
                .andExpect(redirectedUrl("/admin/categories"));
        assertFalse(categories.findById(category.getId()).orElseThrow().isActive());
        mvc.perform(post("/admin/categories/" + category.getId() + "/activate").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/admin/categories"));
        assertTrue(categories.findById(category.getId()).orElseThrow().isActive());
    }

    @Test
    void collectionsCanBeEditedOnlyByTheirOwner() throws Exception {
        User owner = account(RoleName.USER);
        User other = account(RoleName.USER);
        var collection = library.createCollection(owner, "First collection", "Description");
        mvc.perform(get("/library/collections/" + collection.getId()).with(user(CustomUserPrincipal.from(owner))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Sửa tên và mô tả")));
        mvc.perform(post("/library/collections/" + collection.getId()).with(user(CustomUserPrincipal.from(other))).with(csrf())
                        .param("name", "Stolen"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/library/collections/" + collection.getId()).with(user(CustomUserPrincipal.from(owner))).with(csrf())
                        .param("name", "Renamed collection").param("description", "New description"))
                .andExpect(redirectedUrl("/library/collections/" + collection.getId()));
        assertEquals("Renamed collection", library.findOwnedCollection(collection.getId(), owner.getId()).getName());
        library.createCollection(owner, "Duplicate name", "");
        mvc.perform(post("/library/collections/" + collection.getId()).with(user(CustomUserPrincipal.from(owner))).with(csrf())
                        .param("name", "Duplicate name"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("collectionForm", "name"));
    }

    private User account(RoleName role) {
        User account = new User();
        account.setEmail(email()); account.setUsername(account.getEmail()); account.setFullName("Regression User");
        account.setPassword(encoder.encode("Password123"));
        account.setRoles(Set.of(roles.findByName(role).orElseThrow()));
        return users.saveAndFlush(account);
    }

    private MockHttpSession login(User account) throws Exception {
        return (MockHttpSession) Objects.requireNonNull(
                mvc.perform(post("/login").param("email", account.getEmail())
                                .param("password", "Password123").with(csrf()))
                        .andExpect(redirectedUrl("/dashboard"))
                        .andReturn().getRequest().getSession(false),
                "Expected login to create a session");
    }

    private Category category() {
        Category category = new Category(); category.setName("Category " + UUID.randomUUID());
        return categories.saveAndFlush(category);
    }

    private Document document(User owner, DocumentStatus status) {
        Document document = new Document(); document.setTitle("Regression document");
        document.setCreatedBy(owner); document.setStatus(status); document.setFileName("document.pdf");
        document.setFilePath("document.pdf"); document.setFileType("application/pdf");
        return documents.saveAndFlush(document);
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "document.pdf", "application/pdf", "%PDF-1.7 test".getBytes());
    }

    private String email() { return "regression-" + UUID.randomUUID() + "@example.test"; }
}
