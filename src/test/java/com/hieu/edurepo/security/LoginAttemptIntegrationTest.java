package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LoginAttemptIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private LoginAttemptService attempts;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null && users.existsById(userId)) {
            users.deleteById(userId);
        }
    }

    @Test
    void fifthInvalidPasswordLocksLoginAndSuccessfulLoginAfterExpiryResetsState() throws Exception {
        User user = account();

        for (int count = 0; count < 5; count++) {
            invalidLogin(user.getEmail());
        }

        User locked = users.findById(userId).orElseThrow();
        assertEquals(5, locked.getFailedLoginAttempts());
        assertNotNull(locked.getLoginLockedUntil());

        mvc.perform(post("/login").with(csrf())
                        .param("email", user.getEmail())
                        .param("password", "Password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        locked.setLoginLockedUntil(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        users.saveAndFlush(locked);

        mvc.perform(post("/login").with(csrf())
                        .param("email", "  " + user.getEmail().toUpperCase() + "  ")
                        .param("password", "Password123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        User reset = users.findById(userId).orElseThrow();
        assertEquals(0, reset.getFailedLoginAttempts());
        assertNull(reset.getLoginLockedUntil());
    }

    @Test
    void concurrentFailuresCannotLoseUpdatesOrExceedConfiguredThreshold() throws Exception {
        User user = account();
        var executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Void>> work = new ArrayList<>();
            for (int count = 0; count < 8; count++) {
                work.add(() -> {
                    attempts.recordFailure(user.getEmail());
                    return null;
                });
            }
            for (var result : executor.invokeAll(work)) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        User locked = users.findById(userId).orElseThrow();
        assertEquals(5, locked.getFailedLoginAttempts());
        assertNotNull(locked.getLoginLockedUntil());
    }

    private void invalidLogin(String email) throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("email", email)
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    private User account() {
        String email = "login-lock-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("Login lock test");
        user.setPassword(passwords.encode("Password123"));
        user.setEnabled(true);
        user = users.saveAndFlush(user);
        userId = user.getId();
        return user;
    }
}
