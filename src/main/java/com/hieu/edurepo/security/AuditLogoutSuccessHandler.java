package com.hieu.edurepo.security;

import com.hieu.edurepo.enums.AuditAction;
import com.hieu.edurepo.enums.AuditResult;
import com.hieu.edurepo.enums.AuditTargetType;
import com.hieu.edurepo.service.AuditLogService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuditLogoutSuccessHandler implements LogoutSuccessHandler {
    private final AuditLogService auditLogs;
    private final SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

    public AuditLogoutSuccessHandler(AuditLogService auditLogs) {
        this.auditLogs = auditLogs;
        delegate.setDefaultTargetUrl("/login?logout");
    }

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        auditLogs.record(authentication, AuditAction.LOGOUT, AuditTargetType.LOGIN, null,
                "Đăng xuất khỏi EduRepo", AuditResult.SUCCESS);
        delegate.onLogoutSuccess(request, response, authentication);
    }
}
