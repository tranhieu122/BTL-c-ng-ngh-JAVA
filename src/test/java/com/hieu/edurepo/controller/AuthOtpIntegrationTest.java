package com.hieu.edurepo.controller;

import com.hieu.edurepo.entity.AuthOtpToken;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.OtpPurpose;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.AuthOtpTokenRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth_otp;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.upload.dir=target/auth-otp-test-uploads"
})
@AutoConfigureMockMvc
class AuthOtpIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired AuthOtpTokenRepository otpTokens;
    @Autowired PasswordEncoder passwords;

    @MockitoBean EmailService emailService;

    @BeforeEach
    void prepareMail() {
        reset(emailService);
        when(emailService.sendOtp(anyString(), anyString(), anyString())).thenReturn(true);
    }

    @Test
    void registrationWaitsForCorrectOtpBeforeCreatingAccount() throws Exception {
        String email = email();
        MvcResult start = mvc.perform(post("/register").with(csrf())
                        .param("fullName", "OTP Register")
                        .param("email", email)
                        .param("password", "Password123")
                        .param("confirmPassword", "Password123"))
                .andExpect(redirectedUrl("/register/verify"))
                .andReturn();

        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
        String otp = capturedOtp(email);
        AuthOtpToken token = otpTokens.findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                email, OtpPurpose.REGISTER).orElseThrow();
        assertNotEquals(otp, token.getCodeHash());

        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        mvc.perform(post("/register/verify").session(session).with(csrf()).param("code", "000000"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-register"))
                .andExpect(model().attributeHasFieldErrors("otpForm", "code"));
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());

        mvc.perform(post("/register/verify").session(session).with(csrf()).param("code", otp))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("registered", true));
        assertTrue(users.findByEmailIgnoreCase(email).isPresent());
    }

    @Test
    void expiredRegistrationOtpDoesNotCreateAccount() throws Exception {
        String email = email();
        MvcResult start = mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Expired OTP")
                        .param("email", email)
                        .param("password", "Password123")
                        .param("confirmPassword", "Password123"))
                .andExpect(redirectedUrl("/register/verify"))
                .andReturn();

        String otp = capturedOtp(email);
        AuthOtpToken token = otpTokens.findTopByEmailIgnoreCaseAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                email, OtpPurpose.REGISTER).orElseThrow();
        token.setExpiresAt(token.getCreatedAt().minusMinutes(1));
        otpTokens.saveAndFlush(token);

        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        mvc.perform(post("/register/verify").session(session).with(csrf()).param("code", otp))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/verify-register"))
                .andExpect(model().attributeHasFieldErrors("otpForm", "code"));
        assertTrue(users.findByEmailIgnoreCase(email).isEmpty());
    }

    @Test
    void registrationResendIsRateLimited() throws Exception {
        String email = email();
        MvcResult start = mvc.perform(post("/register").with(csrf())
                        .param("fullName", "Cooldown OTP")
                        .param("email", email)
                        .param("password", "Password123")
                        .param("confirmPassword", "Password123"))
                .andExpect(redirectedUrl("/register/verify"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) start.getRequest().getSession(false);
        mvc.perform(post("/register/verify/resend").session(session).with(csrf()))
                .andExpect(redirectedUrl("/register/verify"))
                .andExpect(flash().attributeExists("otpCooldownSeconds"));
        verify(emailService, times(1)).sendOtp(eq(email), anyString(), anyString());
    }

    @Test
    void forgotPasswordDoesNotRevealAccountExistenceAndCanResetAfterOtp() throws Exception {
        User account = account();
        MvcResult existing = mvc.perform(post("/forgot-password").with(csrf()).param("email", account.getEmail()))
                .andExpect(redirectedUrl("/forgot-password/verify"))
                .andExpect(flash().attribute("resetRequested", true))
                .andReturn();

        mvc.perform(post("/forgot-password").with(csrf()).param("email", email()))
                .andExpect(redirectedUrl("/forgot-password/verify"))
                .andExpect(flash().attribute("resetRequested", true));

        String otp = capturedOtp(account.getEmail());
        MockHttpSession session = (MockHttpSession) existing.getRequest().getSession(false);
        mvc.perform(post("/forgot-password/verify").session(session).with(csrf()).param("code", otp))
                .andExpect(redirectedUrl("/reset-password"));

        mvc.perform(post("/reset-password").session(session).with(csrf())
                        .param("password", "RecoveredPassword123")
                        .param("confirmPassword", "RecoveredPassword123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("passwordReset", true));

        User recovered = users.findById(account.getId()).orElseThrow();
        assertTrue(passwords.matches("RecoveredPassword123", recovered.getPassword()));
        assertNull(recovered.getPasswordResetRequestedAt());
    }

    private String capturedOtp(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).sendOtp(eq(email), code.capture(), anyString());
        return code.getAllValues().getLast();
    }

    private User account() {
        Role role = roles.findByName(RoleName.USER).orElseThrow();
        String email = email();
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName("OTP User");
        user.setPassword(passwords.encode("Password123"));
        user.setRoles(Set.of(role));
        return users.saveAndFlush(user);
    }

    private String email() {
        return "otp-" + UUID.randomUUID() + "@example.test";
    }
}
