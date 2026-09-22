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

/**
 * Bộ xử lý sau khi người dùng đăng xuất thành công khỏi hệ thống EduRepo.
 * <p>
 * Tự động ghi nhật ký kiểm toán (Audit Log) về hành vi đăng xuất và chuyển hướng người dùng về trang đăng nhập kèm thông báo.
 * </p>
 */
@Component
public class AuditLogoutSuccessHandler implements LogoutSuccessHandler {

    /** Dịch vụ ghi nhật ký kiểm toán hệ thống */
    private final AuditLogService auditLogs;

    /** Trình ủy quyền chuyển hướng mặc định của Spring Security */
    private final SimpleUrlLogoutSuccessHandler delegate = new SimpleUrlLogoutSuccessHandler();

    public AuditLogoutSuccessHandler(AuditLogService auditLogs) {
        this.auditLogs = auditLogs;
        // Đặt URL chuyển hướng sau khi đăng xuất thành công
        delegate.setDefaultTargetUrl("/login?logout");
    }

    /**
     * Kích hoạt khi người dùng hoàn tất quá trình đăng xuất.
     * Ghi nhận bản ghi kiểm toán sự kiện LOGOUT thành công và điều hướng sang trang login.
     */
    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        // Ghi lại sự kiện đăng xuất an toàn vào bảng audit_logs
        auditLogs.record(authentication, AuditAction.LOGOUT, AuditTargetType.LOGIN, null,
                "Đăng xuất khỏi EduRepo", AuditResult.SUCCESS);

        // Chuyển hướng trình duyệt về URL đích mặc định
        delegate.onLogoutSuccess(request, response, authentication);
    }
}
