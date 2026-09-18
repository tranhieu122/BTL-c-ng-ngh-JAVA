package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.AuditLogRepository;
import com.hieu.edurepo.repository.NotificationRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationSessionPersistenceTest {
    private static final String PASSWORD = "Password123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired NotificationRepository notifications;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PasswordEncoder passwords;

    private Long adminId;
    private Long receiverId;
    private String title;

    @AfterEach
    void cleanUp() {
        if (title != null) {
            notifications.deleteAll(notifications.findAll().stream()
                    .filter(item -> title.equals(item.getTitle())).toList());
        }
        auditLogs.deleteAll(auditLogs.findAll().stream()
                .filter(log -> adminId != null && adminId.equals(log.getActorId())
                        || receiverId != null && receiverId.equals(log.getActorId())).toList());
        if (receiverId != null && users.existsById(receiverId)) users.deleteById(receiverId);
        if (adminId != null && users.existsById(adminId)) users.deleteById(adminId);
    }

    @Test
    void broadcastRemainsInDatabaseAndVisibleAfterLogoutAndLogin() throws Exception {
        User admin = account(RoleName.ADMIN);
        adminId = admin.getId();
        User receiver = account(RoleName.USER);
        receiverId = receiver.getId();
        title = "Thông báo xuyên phiên " + UUID.randomUUID();

        MockHttpSession adminSession = login(admin.getEmail());
        mvc.perform(post("/admin/notifications").session(adminSession).with(csrf())
                        .param("title", title)
                        .param("content", "Nội dung phải còn trong cơ sở dữ liệu sau khi đăng nhập lại")
                        .param("targetType", "ALL")
                        .param("level", "IMPORTANT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notifications"));

        assertTrue(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(receiverId,
                        org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
                .anyMatch(item -> title.equals(item.getTitle())));
        assertTrue(auditLogs.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.SYSTEM_NOTIFICATION_SENT
                && adminId.equals(log.getActorId()) && log.getDescription().contains(title)));

        mvc.perform(post("/logout").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        MockHttpSession receiverSession = login(receiver.getEmail());
        mvc.perform(get("/notifications").session(receiverSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(title)));

        assertTrue(auditLogs.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.LOGIN_SUCCESS
                && receiverId.equals(log.getActorId())));
        assertTrue(auditLogs.findAll().stream().anyMatch(log -> log.getAction() == AuditAction.LOGOUT
                && adminId.equals(log.getActorId())));
    }

    private MockHttpSession login(String email) throws Exception {
        return (MockHttpSession) Objects.requireNonNull(
                mvc.perform(post("/login").with(csrf())
                                .param("email", email).param("password", PASSWORD))
                        .andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/dashboard"))
                        .andReturn().getRequest().getSession(false),
                "Expected login to create a session");
    }

    private User account(RoleName roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        String email = "notification-session-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Kiểm thử thông báo xuyên phiên");
        user.setPassword(passwords.encode(PASSWORD));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        return users.saveAndFlush(user);
    }
}
