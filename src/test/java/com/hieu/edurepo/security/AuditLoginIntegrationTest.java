package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.repository.AuditLogRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLoginIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AuditLogRepository auditLogs;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    private Long userId;

    @AfterEach
    void cleanUp() {
        auditLogs.deleteAll();
        if (userId != null && users.existsById(userId)) users.deleteById(userId);
    }

    @Test
    void failedLoginIsAuditedWithoutPassword() throws Exception {
        User user = new User();
        user.setUsername("audit-login@example.test");
        user.setEmail("audit-login@example.test");
        user.setFullName("Audit login");
        user.setPassword(passwords.encode("CorrectPassword123"));
        user = users.saveAndFlush(user);
        userId = user.getId();

        String submittedPassword = "NeverStoreThisSecret987";
        mockMvc.perform(post("/login").with(csrf()).header("User-Agent", "Audit test browser")
                        .param("email", user.getEmail()).param("password", submittedPassword))
                .andExpect(status().is3xxRedirection());

        var log = auditLogs.findTopByActionOrderByOccurredAtDescIdDesc(AuditAction.LOGIN_FAILURE).orElseThrow();
        assertEquals(AuditResult.FAILURE, log.getResult());
        assertTrue(log.getDescription().contains(user.getEmail()));
        assertFalse(log.getDescription().contains(submittedPassword));
        assertFalse(log.getUserAgent().contains(submittedPassword));
    }
}
