package com.hieu.edurepo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final LoginAttemptService attempts;
    private final com.hieu.edurepo.service.AuditLogService auditLogs;
    private final SimpleUrlAuthenticationSuccessHandler delegate =
            new SimpleUrlAuthenticationSuccessHandler("/dashboard");

    public LoginAuthenticationSuccessHandler(LoginAttemptService attempts,
                                             com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.attempts = attempts;
        this.auditLogs = auditLogs;
        delegate.setAlwaysUseDefaultTargetUrl(true);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            attempts.recordSuccess(principal.getId());
        }
        auditLogs.record(authentication, com.hieu.edurepo.enums.AuditAction.LOGIN_SUCCESS,
                com.hieu.edurepo.enums.AuditTargetType.LOGIN, null,
                "Đăng nhập vào EduRepo", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
