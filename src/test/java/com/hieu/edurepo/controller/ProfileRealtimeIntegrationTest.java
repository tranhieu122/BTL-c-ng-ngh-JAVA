package com.hieu.edurepo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieu.edurepo.entity.*;
import com.hieu.edurepo.enums.*;
import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:profile_realtime;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.upload.dir=target/profile-test-uploads", "app.realtime.interval-ms=100"
})
@AutoConfigureMockMvc
/**
 * Kiểm thử tích hợp đồng bộ thông tin hồ sơ theo thời gian thực (Profile Realtime Integration Test).
 * Xác minh việc cập nhật họ tên, đổi ảnh đại diện và phát sóng tín hiệu đồng bộ đa tab qua kênh SSE.
 */
class ProfileRealtimeIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired DocumentRepository documents;
    @Autowired DocumentVersionRepository versions;
    @Autowired ApprovalHistoryRepository histories;
    @Autowired BookmarkRepository bookmarks;
    @Autowired DocumentCollectionRepository collections;
    @Autowired CollectionItemRepository items;
    @Autowired PasswordEncoder encoder;
    @Autowired ProfileService profiles;
    @Autowired AvatarStorageService avatars;
    @Autowired UserService userService;
    @Autowired LibraryService library;
    @Autowired ReviewService reviews;
    @Autowired ObjectMapper mapper;
    @Autowired FacultyRepository faculties;
    @Autowired DepartmentRepository departments;
    @LocalServerPort int port;

    @Test void everyRoleCanEditOnlyAllowedFieldsOfItsOwnProfile() throws Exception {
        User other = account(RoleName.USER);
        for (RoleName role : RoleName.values()) {
            User owner = account(role); var session = login(owner);
            mvc.perform(get("/profile").session(session)).andExpect(status().isOk())
                    .andExpect(content().string(containsString(owner.getEmail()))).andExpect(content().string(containsString("Hồ sơ cá nhân")));
            mvc.perform(post("/profile").session(session).with(csrf()).param("fullName", "Tên mới")
                    .param("phoneNumber", "+84 123").param("affiliation", "Trường thử").param("bio", "Giới thiệu")
                    .param("id", other.getId().toString()).param("userId", other.getId().toString())
                    .param("email", other.getEmail()).param("roles", "ADMIN").param("enabled", "false")
                    .param("avatarKey", "../other.png").param("password", "InjectedPassword"))
                    .andExpect(redirectedUrl("/profile"));
            var saved = users.findById(owner.getId()).orElseThrow();
            assertEquals("Tên mới", saved.getFullName()); assertEquals("Trường thử", saved.getAffiliation());
            assertEquals(owner.getEmail(), saved.getEmail()); assertTrue(saved.isEnabled());
            assertEquals(Set.of(role), saved.getRoles().stream().map(Role::getName).collect(java.util.stream.Collectors.toSet()));
            assertEquals(owner.getPassword(), saved.getPassword()); assertNull(saved.getAvatarKey());
            mvc.perform(get("/profile").session(login(owner))).andExpect(status().isOk()).andExpect(content().string(containsString("Tên mới")));
        }
        assertEquals("Profile Test", users.findById(other.getId()).orElseThrow().getFullName());
    }

    @Test void anonymousCsrfInvalidFieldsAndForeignUrlsAreRejected() throws Exception {
        mvc.perform(get("/profile")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/profile/avatar")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/events/stream")).andExpect(status().isUnauthorized());
        mvc.perform(get("/events/snapshot")).andExpect(status().isUnauthorized());
        User owner = account(RoleName.USER), other = account(RoleName.USER); var session = login(owner);
        mvc.perform(post("/profile").session(session).param("fullName", "No CSRF")).andExpect(status().isForbidden());
        mvc.perform(post("/profile").session(session).with(csrf()).param("fullName", " ").param("phoneNumber", "<script>"))
                .andExpect(status().isBadRequest()).andExpect(model().attributeHasFieldErrors("profileForm", "fullName", "phoneNumber"));
        mvc.perform(post("/profile").session(session).with(csrf()).param("fullName", "Valid").param("bio", "a".repeat(1001)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/profile/" + other.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(post("/profile/" + other.getId()).session(session).with(csrf()).param("fullName", "Stolen"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/users/" + other.getId() + "/edit").session(session)).andExpect(status().isForbidden());
        assertEquals("Profile Test", profiles.get(owner.getId()).fullName());
    }

    @Test void avatarValidationReplacementPersistenceAndOwnership() throws Exception {
        User owner = account(RoleName.USER), other = account(RoleName.USER); var session = login(owner);
        byte[] png = png();
        mvc.perform(multipart("/profile/avatar").file(new MockMultipartFile("avatar", "face.png", "image/png", png))
                .session(session).with(csrf())).andExpect(redirectedUrl("/profile"));
        String first = profiles.get(owner.getId()).avatarRevision();
        assertTrue(Files.exists(Path.of("target/profile-test-uploads/avatars", first)));
        mvc.perform(get("/profile/avatar").session(login(owner))).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/profile/avatar").session(login(other)).param("userId", owner.getId().toString()).param("v", first))
                .andExpect(content().contentType("image/svg+xml"));
        for (var invalid : List.of(
                new MockMultipartFile("avatar", "face.svg", "image/svg+xml", "<svg/>".getBytes()),
                new MockMultipartFile("avatar", "fake.png", "image/png", "<script>alert(1)</script>".getBytes()),
                new MockMultipartFile("avatar", "large.png", "image/png", new byte[2 * 1024 * 1024 + 1]),
                new MockMultipartFile("avatar", "wide.png", "image/png", imageBytes("png", 4097, 1)),
                new MockMultipartFile("avatar", "face.png", "application/pdf", png),
                new MockMultipartFile("avatar", "empty.png", "image/png", new byte[0]))) {
            mvc.perform(multipart("/profile/avatar").file(invalid).session(session).with(csrf()))
                    .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("error"));
            assertEquals(first, profiles.get(owner.getId()).avatarRevision());
        }
        // Even a mistakenly supplied foreign storage key cannot remove another user's file.
        avatars.delete(other.getId(), first); avatars.delete(owner.getId(), "../default-avatar.svg");
        assertTrue(Files.exists(Path.of("target/profile-test-uploads/avatars", first)));
        mvc.perform(multipart("/profile/avatar").file(new MockMultipartFile("avatar", "next.jpg", "image/jpeg", imageBytes("jpeg", 16, 16)))
                .session(session).with(csrf())).andExpect(redirectedUrl("/profile"));
        String next = profiles.get(owner.getId()).avatarRevision(); assertNotEquals(first, next);
        assertFalse(Files.exists(Path.of("target/profile-test-uploads/avatars", first)));
        mvc.perform(post("/profile/avatar/delete").session(session).with(csrf()).param("id", other.getId().toString()))
                .andExpect(redirectedUrl("/profile"));
        assertFalse(Files.exists(Path.of("target/profile-test-uploads/avatars", next)));
        mvc.perform(get("/profile/avatar").session(login(owner))).andExpect(content().contentType("image/svg+xml"));
    }

    @Test void passwordChangeRequiresCurrentConfirmationAndPolicyAndRevokesBothSessions() throws Exception {
        User owner = account(RoleName.USER); var first = login(owner); var second = login(owner);
        for (String[] input : List.of(new String[]{"wrong", "NewPassword123", "NewPassword123"},
                new String[]{"Password123", "NewPassword123", "mismatch"},
                new String[]{"Password123", "short", "short"},
                new String[]{"Password123", "ắ".repeat(25), "ắ".repeat(25)},
                new String[]{"Password123", "Password123", "Password123"})) {
            mvc.perform(post("/profile/password").session(first).with(csrf()).param("currentPassword", input[0])
                    .param("newPassword", input[1]).param("confirmPassword", input[2]))
                    .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("error"));
            assertEquals(owner.getPassword(), users.findById(owner.getId()).orElseThrow().getPassword());
        }
        mvc.perform(post("/profile/password").session(first).with(csrf()).param("currentPassword", "Password123")
                .param("newPassword", "NewPassword123").param("confirmPassword", "NewPassword123"))
                .andExpect(redirectedUrl("/login?passwordChanged"));
        assertTrue(first.isInvalid());
        mvc.perform(get("/profile").session(second)).andExpect(redirectedUrl("/login?expired"));
        assertTrue(second.isInvalid());
        mvc.perform(post("/login").with(csrf()).param("email", owner.getEmail()).param("password", "Password123"))
                .andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).param("email", owner.getEmail()).param("password", "NewPassword123"))
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test void deletingTestAccountReauthenticatesScrubsPrivateDataAndRetainsBusinessReferences() throws Exception {
        User owner = account(RoleName.REVIEWER), other = account(RoleName.USER);
        Document document = document(owner, DocumentStatus.PUBLISHED);
        var version = new DocumentVersion(); version.setDocument(document); version.setCreatedBy(owner);
        version.setFileName("retained.pdf"); version.setFilePath("retained.pdf"); version.setVersionNumber(1); versions.saveAndFlush(version);
        var history = new ApprovalHistory(); history.setDocument(document); history.setReviewer(owner); history.setAction(ReviewAction.PUBLISHED); histories.saveAndFlush(history);
        library.toggleBookmark(owner, document.getId()); library.toggleBookmark(other, document.getId());
        var collection = library.createCollection(owner, "Private collection", "Private description");
        library.addToCollection(collection.getId(), document.getId(), owner.getId());
        profiles.uploadAvatar(owner.getId(), new MockMultipartFile("avatar", "face.png", "image/png", png()));
        String key = profiles.get(owner.getId()).avatarRevision();
        var session = login(owner); var second = login(owner);
        for (String[] input : List.of(new String[]{"wrong", "XÓA TÀI KHOẢN", "true"},
                new String[]{"Password123", "wrong", "true"}, new String[]{"Password123", "XÓA TÀI KHOẢN", "false"})) {
            mvc.perform(post("/profile/delete").session(session).with(csrf()).param("currentPassword", input[0])
                    .param("confirmation", input[1]).param("acknowledged", input[2]))
                    .andExpect(redirectedUrl("/profile")).andExpect(flash().attributeExists("error"));
            assertNull(users.findById(owner.getId()).orElseThrow().getDeletedAt());
        }
        mvc.perform(post("/profile/delete").session(session).with(csrf()).param("currentPassword", "Password123")
                .param("confirmation", "XÓA TÀI KHOẢN").param("acknowledged", "true").param("id", other.getId().toString()))
                .andExpect(redirectedUrl("/login?accountDeleted"));
        var deleted = users.findById(owner.getId()).orElseThrow();
        assertFalse(deleted.isEnabled()); assertNotNull(deleted.getDeletedAt()); assertTrue(deleted.getRoles().isEmpty());
        assertEquals("Tài khoản đã xóa", deleted.getFullName()); assertNotEquals(owner.getEmail(), deleted.getEmail());
        assertNull(deleted.getAvatarKey()); assertFalse(Files.exists(Path.of("target/profile-test-uploads/avatars", key)));
        assertTrue(bookmarks.findDetailedByUserId(owner.getId()).isEmpty()); assertFalse(collections.existsById(collection.getId()));
        assertEquals(0, items.countByCollectionId(collection.getId()));
        assertEquals(1, bookmarks.findDetailedByUserId(other.getId()).size()); assertTrue(users.findById(other.getId()).orElseThrow().isEnabled());
        assertEquals("Tài khoản đã xóa", documents.findById(document.getId()).orElseThrow().getCreatedBy().getFullName());
        assertTrue(versions.existsById(version.getId())); assertTrue(histories.existsById(history.getId()));
        assertFalse(userService.findAll().stream().anyMatch(user -> user.getId().equals(owner.getId())));
        assertThrows(com.hieu.edurepo.exception.ResourceNotFoundException.class, () -> userService.save(owner, false));
        mvc.perform(get("/profile").session(second)).andExpect(redirectedUrl("/login?expired"));
        mvc.perform(post("/login").with(csrf()).param("email", owner.getEmail()).param("password", "Password123"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test void lastActiveAdminCannotDeleteItself() {
        var existing = users.findAll().stream().filter(user -> user.isEnabled() && user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN)).toList();
        existing.forEach(user -> { user.setEnabled(false); users.saveAndFlush(user); });
        User admin = account(RoleName.ADMIN);
        try {
            assertThrows(IllegalArgumentException.class, () -> profiles.deleteAccount(admin.getId(), "Password123", "XÓA TÀI KHOẢN", true));
            assertTrue(users.findById(admin.getId()).orElseThrow().isEnabled());
        } finally { existing.forEach(user -> { user.setEnabled(true); users.saveAndFlush(user); }); }
    }

    @Test void deletedBootstrapLoginIsNotRecreatedAndStaleAdminEditPreservesProfile() throws Exception {
        User owner = account(RoleName.USER);
        var initializer = new com.hieu.edurepo.config.DataInitializer(roles, users, faculties, departments, encoder,
                "", "", owner.getEmail(), "Password123");
        initializer.run();
        var staleAdminCopy = users.findById(owner.getId()).orElseThrow();
        var form = profiles.get(owner.getId()).form(); form.setAffiliation("Updated concurrently");
        profiles.update(owner.getId(), form);
        profiles.uploadAvatar(owner.getId(), new MockMultipartFile("avatar", "face.png", "image/png", png()));
        String avatar = profiles.get(owner.getId()).avatarRevision();
        staleAdminCopy.setFullName("Admin renamed"); userService.save(staleAdminCopy, false);
        assertEquals("Updated concurrently", profiles.get(owner.getId()).affiliation());
        assertEquals(avatar, profiles.get(owner.getId()).avatarRevision());
        profiles.deleteAccount(owner.getId(), "Password123", "XÓA TÀI KHOẢN", true);
        initializer.run();
        assertTrue(users.findByEmailIgnoreCase(owner.getEmail()).isEmpty());
    }

    @Test void realHttpSseIsIsolatedAcrossThreeSessionsAndResynchronizesAfterReconnect() throws Exception {
        User owner = account(RoleName.SUBMITTER), other = account(RoleName.SUBMITTER), reviewer = account(RoleName.REVIEWER);
        Document ownDoc = document(owner, DocumentStatus.UNDER_REVIEW), otherDoc = document(other, DocumentStatus.DRAFT);
        try (var first = new Browser(owner); var second = new Browser(other); var review = new Browser(reviewer);
             var ownerStream = first.stream(); var otherStream = second.stream(); var reviewStream = review.stream()) {
            JsonNode initial = ownerStream.snapshot(), otherInitial = otherStream.snapshot(), queue = reviewStream.snapshot();
            assertEquals(owner.getEmail(), initial.at("/profile/email").asText());
            assertFalse(initial.toString().contains(other.getEmail())); assertFalse(initial.toString().contains(otherDoc.getTitle()));
            assertFalse(otherInitial.toString().contains(ownDoc.getTitle())); assertTrue(otherInitial.get("reviewQueue").isEmpty());
            assertTrue(queue.get("reviewQueue").toString().contains(ownDoc.getTitle()));
            assertFalse(queue.toString().contains(otherDoc.getTitle())); assertFalse(queue.toString().contains(owner.getEmail()));
            assertEquals(302, first.post("/profile", Map.of("fullName", "Updated over HTTP", "userId", other.getId().toString())).statusCode());
            assertEquals("Updated over HTTP", ownerStream.snapshot().at("/profile/fullName").asText());
            assertNull(otherStream.events.poll(400, TimeUnit.MILLISECONDS));
            assertEquals(302, review.post("/reviews/" + ownDoc.getId(), Map.of("action", "APPROVED", "comment", "Good test material", "contentQualityScore", "4", "teachingEffectivenessScore", "4", "easeOfUseScore", "4")).statusCode());
            assertEquals("APPROVED", ownerStream.snapshot().at("/documents/0/status").asText());
            assertTrue(reviewStream.snapshot().get("reviewQueue").toString().contains("APPROVED"));
            ownerStream.close();
            assertEquals(302, first.post("/profile", Map.of("fullName", "Changed while disconnected")).statusCode());
            try (var reconnected = first.stream()) {
                assertEquals("Changed while disconnected", reconnected.snapshot().at("/profile/fullName").asText());
                assertEquals(302, first.post("/profile/password", Map.of("currentPassword", "Password123", "newPassword", "NewPassword123", "confirmPassword", "NewPassword123")).statusCode());
                assertEquals("session-expired", reconnected.next().name());
                assertEquals(401, first.get("/events/snapshot").statusCode());
            }
            assertEquals(other.getEmail(), mapper.readTree(second.get("/events/snapshot?userId=" + owner.getId()).body()).at("/profile/email").asText());
            assertEquals(302, second.post("/logout", Map.of()).statusCode());
            assertEquals("session-expired", otherStream.next().name());
            assertEquals(401, second.get("/events/stream").statusCode());
            reviewer.setRoles(Set.of(roles.findByName(RoleName.USER).orElseThrow()));
            userService.save(reviewer, false);
            assertEquals("session-expired", reviewStream.next().name());
            assertEquals(401, review.get("/events/snapshot").statusCode());
        }
    }

    private User account(RoleName role) {
        var user = new User(); user.setEmail("profile-" + UUID.randomUUID() + "@example.test"); user.setUsername(user.getEmail());
        user.setFullName("Profile Test"); user.setPassword(encoder.encode("Password123"));
        user.setRoles(Set.of(roles.findByName(role).orElseThrow())); return users.saveAndFlush(user);
    }
    private Document document(User owner, DocumentStatus state) {
        var doc = new Document(); doc.setTitle("Private document " + UUID.randomUUID()); doc.setCreatedBy(owner); doc.setStatus(state);
        doc.setFileName("test.pdf"); doc.setFilePath("test.pdf"); return documents.saveAndFlush(doc);
    }
    private MockHttpSession login(User user) throws Exception {
        return (MockHttpSession) Objects.requireNonNull(
                mvc.perform(post("/login").with(csrf()).param("email", user.getEmail()).param("password", "Password123"))
                        .andExpect(redirectedUrl("/dashboard")).andReturn().getRequest().getSession(false),
                "Expected login to create a session");
    }
    private byte[] png() throws IOException {
        return imageBytes("png", 16, 16);
    }
    private byte[] imageBytes(String format, int width, int height) throws IOException {
        var output = new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output); return output.toByteArray();
    }
    private final class Browser implements AutoCloseable {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        Browser(User user) throws Exception { assertEquals(302, post("/login", Map.of("email", user.getEmail(), "password", "Password123")).statusCode()); }
        URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
        HttpResponse<String> get(String path) throws Exception { return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        HttpResponse<String> post(String path, Map<String, String> fields) throws Exception {
            var token = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(get(path.equals("/login") ? "/login" : "/profile").body());
            assertTrue(token.find(), "CSRF input missing"); var values = new HashMap<>(fields); values.put("_csrf", token.group(1));
            String body = values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
            return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        Stream stream() throws Exception {
            var response = client.send(HttpRequest.newBuilder(uri("/events/stream")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200, response.statusCode()); assertTrue(response.headers().firstValue("content-type").orElse("").contains("text/event-stream"));
            return new Stream(response.body());
        }
        public void close() { }
    }
    private record Event(String name, String data) { }
    private final class Stream implements AutoCloseable {
        final InputStream input;
        final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
        final Thread reader;
        Stream(InputStream input) {
            this.input = input;
            reader = new Thread(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line, name = "", data = "";
                    while ((line = lines.readLine()) != null) {
                        if (line.startsWith("event:")) name = line.substring(6).strip();
                        if (line.startsWith("data:")) data += line.substring(5).strip();
                        if (line.isEmpty()) { if (!name.isEmpty()) events.add(new Event(name, data)); name = ""; data = ""; }
                    }
                } catch (IOException ignored) { }
            });
            reader.start();
        }
        Event next() throws Exception { var event = events.poll(10, TimeUnit.SECONDS); assertNotNull(event, "Timed out waiting for SSE event"); return event; }
        JsonNode snapshot() throws Exception { var event = next(); assertEquals("snapshot", event.name()); return mapper.readTree(event.data()); }
        public void close() throws IOException { input.close(); reader.interrupt(); }
    }
}
