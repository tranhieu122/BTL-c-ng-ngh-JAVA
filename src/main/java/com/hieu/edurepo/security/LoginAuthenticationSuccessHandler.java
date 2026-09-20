package com.hieu.edurepo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handler xử lý sự kiện đăng nhập thành công.
 *
 * <p>Thực hiện các tác vụ sau khi người dùng đăng nhập thành công:</p>
 * <ol>
 *   <li>Reset bộ đếm đăng nhập thất bại ({@code failedLoginAttempts = 0}) và xóa thời gian khóa.</li>
 *   <li>Ghi audit log sự kiện đăng nhập thành công.</li>
 *   <li>Chuyển hướng người dùng về {@code /dashboard} (sẽ được phân tuyến tiếp theo vai trò).</li>
 * </ol>
 *
 * <p>Ủy quyền việc redirect cho {@link SimpleUrlAuthenticationSuccessHandler} với
 * {@code alwaysUseDefaultTargetUrl=true} để tránh người dùng bị gửi về URL trước đó
 * một cách không mong muốn.</p>
 */
@Component
public class LoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    /** Service ghi nhận đăng nhập thành công (reset bộ đếm thất bại). */
    private final LoginAttemptService attempts;

    /** Service ghi audit log sự kiện đăng nhập. */
    private final com.hieu.edurepo.service.AuditLogService auditLogs;

    /**
     * Delegate handler để xử lý redirect về /dashboard sau khi đăng nhập.
     * Luôn dùng URL mặc định, không phụ thuộc vào "saved request" trước đó.
     */
    private final SimpleUrlAuthenticationSuccessHandler delegate =
            new SimpleUrlAuthenticationSuccessHandler("/dashboard");

    /**
     * Constructor inject các dependency.
     *
     * @param attempts  Service ghi nhận kết quả đăng nhập.
     * @param auditLogs Service ghi audit log.
     */
    public LoginAuthenticationSuccessHandler(LoginAttemptService attempts,
                                             com.hieu.edurepo.service.AuditLogService auditLogs) {
        this.attempts = attempts;
        this.auditLogs = auditLogs;
        delegate.setAlwaysUseDefaultTargetUrl(true);
    }

    /**
     * Xử lý sau khi Spring Security xác thực thành công.
     *
     * @param request        HTTP request hiện tại.
     * @param response       HTTP response để thực hiện redirect.
     * @param authentication Thông tin xác thực của người dùng vừa đăng nhập.
     * @throws IOException      Nếu lỗi I/O khi redirect.
     * @throws ServletException Nếu lỗi servlet.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        // Reset bộ đếm thất bại nếu người dùng là CustomUserPrincipal
        if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            attempts.recordSuccess(principal.getId());
        }
        // Ghi audit log sự kiện đăng nhập thành công
        auditLogs.record(authentication, com.hieu.edurepo.enums.AuditAction.LOGIN_SUCCESS,
                com.hieu.edurepo.enums.AuditTargetType.LOGIN, null,
                "Đăng nhập vào EduRepo", com.hieu.edurepo.enums.AuditResult.SUCCESS);
        // Redirect về /dashboard
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
