package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.NotificationRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.security.CustomUserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminNotificationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private NotificationRepository notifications;
    @Autowired private PasswordEncoder passwords;
    @Autowired private EntityManager entityManager;

    @Test
    void adminCanSendNotificationToSpecificUser() throws Exception {
        User admin = account(RoleName.ADMIN);
        User receiver = account(RoleName.USER);

        mvc.perform(post("/admin/notifications")
                        .param("title", "Lịch bảo trì")
                        .param("content", "EduRepo bảo trì lúc 21:00.")
                        .param("targetType", "USER")
                        .param("receiverId", receiver.getId().toString())
                        .param("level", "IMPORTANT")
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/notifications"))
                .andExpect(flash().attribute("success", "Gửi thông báo thành công"));

        var saved = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                receiver.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        assertEquals(1, saved.size());
        var notification = saved.get(0);
        assertEquals("Lịch bảo trì", notification.getTitle());
        assertEquals("EduRepo bảo trì lúc 21:00.", notification.getContent());
        assertEquals("IMPORTANT", notification.getLevel().name());
        assertEquals(admin.getId(), notification.getSender().getId());
        assertNotNull(notification.getCreatedAt());
    }

    @Test
    void blankTitleOrContentIsRejected() throws Exception {
        User admin = account(RoleName.ADMIN);
        User receiver = account(RoleName.USER);

        mvc.perform(post("/admin/notifications")
                        .param("title", " ")
                        .param("content", " ")
                        .param("targetType", "USER")
                        .param("receiverId", receiver.getId().toString())
                        .param("level", "NORMAL")
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notifications"))
                .andExpect(model().attributeHasFieldErrors("notificationForm", "title", "content"));

        assertTrue(notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                receiver.getId(), org.springframework.data.domain.Pageable.unpaged()).isEmpty());
    }

    @Test
    void missingReceiverTargetIsRejected() throws Exception {
        User admin = account(RoleName.ADMIN);

        mvc.perform(post("/admin/notifications")
                        .param("title", "Thông báo")
                        .param("content", "Nội dung")
                        .param("targetType", "USER")
                        .param("level", "NORMAL")
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/notifications"))
                .andExpect(model().attributeHasFieldErrors("notificationForm", "receiverId"));
    }

    @Test
    void normalUserCannotOpenAdminNotificationPage() throws Exception {
        User account = account(RoleName.USER);

        mvc.perform(get("/admin/notifications").with(user(CustomUserPrincipal.from(account))))
                .andExpect(status().isForbidden());
    }

    @Test
    void userOnlySeesOwnNotificationsAndCanMarkRead() throws Exception {
        User admin = account(RoleName.ADMIN);
        User first = account(RoleName.USER);
        User second = account(RoleName.USER);
        send(admin, first, "Thông báo riêng");
        send(admin, second, "Thông báo người khác");

        mvc.perform(get("/notifications/api").with(user(CustomUserPrincipal.from(first))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value("Thông báo riêng"));

        Long notificationId = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                first.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent().get(0).getId();

        mvc.perform(get("/notifications/api/unread-count").with(user(CustomUserPrincipal.from(first))))
                .andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/notifications/api/{id}/read", notificationId)
                        .with(user(CustomUserPrincipal.from(first)))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/notifications/api/unread-count").with(user(CustomUserPrincipal.from(first))))
                .andExpect(jsonPath("$.count").value(0));

        entityManager.flush();
        entityManager.clear();
        assertTrue(notifications.findById(notificationId).orElseThrow().isRead());
        assertNotNull(notifications.findById(notificationId).orElseThrow().getReadAt());
    }

    @Test
    void notificationPagesRenderForUser() throws Exception {
        User admin = account(RoleName.ADMIN);
        User receiver = account(RoleName.USER);
        send(admin, receiver, "Trang thông báo");

        Long notificationId = notifications.findByRecipientIdOrderByCreatedAtDescIdDesc(
                receiver.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent().get(0).getId();

        mvc.perform(get("/notifications").with(user(CustomUserPrincipal.from(receiver))))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/index"))
                .andExpect(content().string(containsString("Trang thông báo")));
        mvc.perform(get("/notifications/{id}", notificationId).with(user(CustomUserPrincipal.from(receiver))))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/detail"))
                .andExpect(content().string(containsString("Trang thông báo")));
    }

    private void send(User admin, User receiver, String title) throws Exception {
        mvc.perform(post("/admin/notifications")
                        .param("title", title)
                        .param("content", "Nội dung " + title)
                        .param("targetType", "USER")
                        .param("receiverId", receiver.getId().toString())
                        .param("level", "NORMAL")
                        .with(user(CustomUserPrincipal.from(admin)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    private User account(RoleName roleName) {
        Role role = roles.findByName(roleName).orElseThrow();
        String email = "admin-notification-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Người dùng " + roleName);
        user.setPassword(passwords.encode("Password123"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        return users.saveAndFlush(user);
    }
}
