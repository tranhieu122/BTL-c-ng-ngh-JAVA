package com.hieu.edurepo.security;

import com.hieu.edurepo.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Filter bảo mật kiểm tra tính hợp lệ của session người dùng tại mỗi request.
 *
 * <p>Vấn đề: khi người dùng đăng nhập, Spring Security tạo một "snapshot" (Principal)
 * chứa thông tin tài khoản tại thời điểm đó. Nếu admin sau đó thay đổi quyền,
 * đổi mật khẩu, xóa hoặc vô hiệu hóa tài khoản → session cũ vẫn còn hiệu lực
 * cho đến khi người dùng đăng xuất tự nguyện.</p>
 *
 * <p>Giải pháp: Filter này so sánh thông tin trong session với CSDL tại mỗi request.
 * Nếu phát hiện mâu thuẫn (mật khẩu, email, roles, trạng thái tài khoản), sẽ
 * cưỡng bức đăng xuất ngay lập tức:</p>
 * <ul>
 *   <li>Với request SSE ({@code /events/*}): trả về HTTP 401.</li>
 *   <li>Với request HTML thông thường: redirect về {@code /login?expired}.</li>
 * </ul>
 *
 * <p>Được thêm vào filter chain trước {@code AuthorizationFilter} trong {@code SecurityConfig}.</p>
 */
public class AccountSessionFilter extends OncePerRequestFilter {

    /** Repository để truy vấn thông tin tài khoản mới nhất từ CSDL. */
    private final UserRepository users;

    /**
     * @param users Repository người dùng.
     */
    public AccountSessionFilter(UserRepository users) {
        this.users = users;
    }

    /**
     * Kiểm tra session mỗi request một lần.
     *
     * @param request   HTTP request.
     * @param response  HTTP response.
     * @param chain     Filter chain tiếp theo.
     * @throws ServletException Nếu lỗi servlet.
     * @throws IOException      Nếu lỗi I/O.
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            var account = users.findById(principal.getId()).orElse(null);
            // Principal trong session là ảnh chụp tại thời điểm đăng nhập.
            // Cần so sánh password/email/role/enabled với database để phát hiện thay đổi.
            var sessionRoles = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
            boolean valid = account != null && account.isEnabled() && account.getDeletedAt() == null
                    && Objects.equals(account.getPassword(), principal.getPassword())
                    && Objects.equals(account.getEmail(), principal.getUsername())
                    && account.getRoles().stream().map(role -> "ROLE_" + role.getName().name())
                        .collect(Collectors.toSet()).equals(sessionRoles);
            if (!valid) {
                // Session không còn hợp lệ → đăng xuất cưỡng bức để quyền mới có hiệu lực ngay.
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/events/")) response.setStatus(401);
                else response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
