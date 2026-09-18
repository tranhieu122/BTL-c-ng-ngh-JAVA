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

/** Reject stale sessions after account deletion, suspension, password or role changes. */
public class AccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountSessionFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
            var account = users.findById(principal.getId()).orElse(null);
            // Principal trong session là ảnh chụp tại thời điểm đăng nhập.
            // Vì vậy cần so lại password/email/role/enabled với database ở mỗi request quan trọng.
            var sessionRoles = principal.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
            boolean valid = account != null && account.isEnabled() && account.getDeletedAt() == null
                    && Objects.equals(account.getPassword(), principal.getPassword())
                    && Objects.equals(account.getEmail(), principal.getUsername())
                    && account.getRoles().stream().map(role -> "ROLE_" + role.getName().name())
                        .collect(Collectors.toSet()).equals(sessionRoles);
            if (!valid) {
                // Nếu thông tin session đã cũ, đăng xuất cưỡng bức để quyền mới có hiệu lực ngay.
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/events/")) response.setStatus(401);
                else response.sendRedirect(request.getContextPath() + "/login?expired");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
