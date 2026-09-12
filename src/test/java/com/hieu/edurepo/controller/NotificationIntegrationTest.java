package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Document;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.DocumentStatus;
import com.hieu.edurepo.enums.NotificationType;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.NotificationRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import com.hieu.edurepo.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private DocumentRepository documents;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private PasswordEncoder passwords;
    private Long authorId;
    private Long otherId;
    private Long documentId;

    @AfterEach
    void cleanUp() {
        if (authorId != null) {
            notificationRepository.deleteAll(notificationRepository
                    .findByRecipientIdOrderByCreatedAtDescIdDesc(authorId,
                            org.springframework.data.domain.Pageable.unpaged()).getContent());
        }
        if (documentId != null && documents.existsById(documentId)) documents.deleteById(documentId);
        if (authorId != null && users.existsById(authorId)) users.deleteById(authorId);
        if (otherId != null && users.existsById(otherId)) users.deleteById(otherId);
    }

    @Test
    void notificationHistoryIsPrivateAndReadActionsRequireCsrf() throws Exception {
        User author = account();
        authorId = author.getId();
        User other = account();
        otherId = other.getId();
        Document document = new Document();
        document.setTitle("Giáo trình kiểm thử thông báo");
        document.setStatus(DocumentStatus.APPROVED);
        document.setCreatedBy(author);
        document = documents.saveAndFlush(document);
        documentId = document.getId();

        notificationService.reviewed(document, NotificationType.DOCUMENT_APPROVED,
                "notification-test:" + UUID.randomUUID());

        String notificationId = notificationRepository
                .findByRecipientIdOrderByCreatedAtDescIdDesc(author.getId(), org.springframework.data.domain.Pageable.unpaged())
                .getContent().getFirst().getId().toString();

        mvc.perform(get("/notifications/api").with(user(CustomUserPrincipal.from(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Tài liệu đã được phê duyệt"))
                .andExpect(jsonPath("$.items[0].read").value(false));
        mvc.perform(get("/notifications/api").with(user(CustomUserPrincipal.from(other))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(post("/notifications/api/{id}/read", notificationId)
                        .with(user(CustomUserPrincipal.from(author))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/notifications/api/{id}/read", notificationId)
                        .with(user(CustomUserPrincipal.from(other))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/notifications/api/unread-count").with(user(CustomUserPrincipal.from(author))))
                .andExpect(jsonPath("$.count").value(1));

        mvc.perform(post("/notifications/api/{id}/read", notificationId)
                        .with(user(CustomUserPrincipal.from(author))).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/notifications/api/unread-count").with(user(CustomUserPrincipal.from(author))))
                .andExpect(jsonPath("$.count").value(0));
    }

    private User account() {
        String email = "notification-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Notification test");
        user.setPassword(passwords.encode("Password123"));
        user.setEnabled(true);
        return users.saveAndFlush(user);
    }
}
