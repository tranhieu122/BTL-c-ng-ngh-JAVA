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

/**
 * Bộ xử lý khi xác thực đăng nhập thất bại (sai email, sai mật khẩu, hoặc tài khoản bị khóa).
 * <p>
 * Tăng số lần thử sai để kích hoạt cơ chế bảo vệ brute-force, ghi lại Audit Log thất bại kèm IP và chuyển hướng kèm cờ lỗi.
 * </p>
 */
@Component
public class LoginAuthenticationFailureHandler implements AuthenticationFailureHandler {

    /** Dịch vụ ghi nhận và đếm số lần đăng nhập sai */
    private final LoginAttemptService attempts;

    /** Dịch vụ ghi nhật ký kiểm toán hệ thống */
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    /** Trình ủy quyền chuyển hướng mặc định về trang đăng nhập kèm tham số ?error */
    private final SimpleUrlAuthenticationFailureHandler delegate =
            new SimpleUrlAuthenticationFailureHandler("/login?error");

    public LoginAuthenticationFailureHandler(LoginAttemptService attempts,
                                             com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.attempts = attempts;
        this.auditLogs = auditLogs;
    }

    /**
     * Kích hoạt tự động khi Spring Security từ chối xác thực đăng nhập.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        // Nếu là lỗi nhập sai thông tin đăng nhập thì ghi nhận tăng biến đếm số lần thử sai
        if (exception instanceof BadCredentialsException) {
            attempts.recordFailure(request.getParameter("email"));
        }

        String email = request.getParameter("email");

        // Ghi lại nhật ký kiểm toán cho sự kiện đăng nhập thất bại nhằm phát hiện hành vi tấn công
        auditLogs.recordAnonymous(email, com.hieu.edurepo.enums.AuditAction.LOGIN_FAILURE,
                com.hieu.edurepo.enums.AuditTargetType.LOGIN, null,
                "Đăng nhập thất bại với email " + (email == null ? "không xác định" : email.trim()),
                com.hieu.edurepo.enums.AuditResult.FAILURE);

        // Chuyển hướng người dùng về trang đăng nhập với thông báo lỗi thân thiện
        delegate.onAuthenticationFailure(request, response, exception);
    }
}
