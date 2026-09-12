package com.hieu.edurepo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final LoginAttemptService attempts;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;
    private final SimpleUrlAuthenticationFailureHandler delegate =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    public LoginAuthenticationFailureHandler(LoginAttemptService attempts,
                                             com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.attempts = attempts;
        this.auditLogs = auditLogs;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        if (exception instanceof BadCredentialsException) {
            attempts.recordFailure(request.getParameter("email"));
        }
        String email = request.getParameter("email");
        auditLogs.recordAnonymous(email, com.hieu.edurepo.enums.AuditAction.LOGIN_FAILURE,
                com.hieu.edurepo.enums.AuditTargetType.LOGIN, null,
                "Đăng nhập thất bại với email " + (email == null ? "không xác định" : email.trim()),
                com.hieu.edurepo.enums.AuditResult.FAILURE);
        delegate.onAuthenticationFailure(request, response, exception);
    }
}
