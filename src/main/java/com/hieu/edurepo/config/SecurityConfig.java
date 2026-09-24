package com.hieu.edurepo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật Spring Security cho hệ thống EduRepo.
 *
 * <p>Cung cấp các bean quan trọng:</p>
 * <ul>
 *   <li>{@link #passwordEncoder()} – Mã hóa mật khẩu BCrypt.</li>
 *   <li>{@link #applicationClock()} – Đồng hồ UTC dùng trong toàn ứng dụng.</li>
 *   <li>{@link #securityFilterChain(HttpSecurity, com.hieu.edurepo.repository.UserRepository,
 *       com.hieu.edurepo.security.LoginAuthenticationSuccessHandler,
 *       com.hieu.edurepo.security.LoginAuthenticationFailureHandler,
 *       com.hieu.edurepo.security.AuditLogoutSuccessHandler)} – Cấu hình chuỗi filter bảo mật.</li>
 * </ul>
 *
 * <h3>Phân quyền truy cập:</h3>
 * <ul>
 *   <li>Public: trang chủ, kho tài liệu, đăng nhập/đăng ký/OTP, static resources.</li>
 *   <li>ADMIN only: {@code /admin/**}, Actuator endpoints (trừ health check).</li>
 *   <li>REVIEWER/ADMIN: {@code /moderation/**}, {@code /reviews/**}.</li>
 *   <li>SUBMITTER/ADMIN: {@code /documents/**}.</li>
 *   <li>USER: có thể gửi yêu cầu Submitter.</li>
 * </ul>
 *
 * <p>Bảo vệ bổ sung: {@code AccountSessionFilter} kiểm tra tài khoản mỗi request để
 * phát hiện bị khóa/vô hiệu hóa sau khi đăng nhập.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Bean mã hóa mật khẩu dùng BCrypt với strength mặc định (10 rounds).
     * Được inject vào {@code UserService} và {@code AuthController}.
     *
     * @return BCryptPasswordEncoder instance.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Bean đồng hồ UTC của ứng dụng, dùng trong các service liên quan đến thời gian
     * như OTP, Rate Limiter, lockout, để dễ test và môi trường cloud đa timezone.
     *
     * @return {@code Clock.systemUTC()} – đồng hồ UTC hệ thống.
     */
    @Bean
    public java.time.Clock applicationClock() {
        return java.time.Clock.systemUTC();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            com.hieu.edurepo.repository.UserRepository users,
            com.hieu.edurepo.security.LoginAuthenticationSuccessHandler successHandler,
            com.hieu.edurepo.security.LoginAuthenticationFailureHandler failureHandler,
            com.hieu.edurepo.security.AuditLogoutSuccessHandler logoutSuccessHandler) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/document-assistant/**", "/api/chat-sessions/**"))
                // Mỗi request đã đăng nhập được kiểm tra lại với database để phát hiện tài khoản bị khóa,
                // đổi mật khẩu hoặc đổi quyền sau thời điểm login.
                .addFilterBefore(new com.hieu.edurepo.security.AccountSessionFilter(users),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // The initial SSE request is authenticated; completion may happen after logout.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        // Các trang công khai: xem kho, tải/xem tài liệu đã publish, đăng nhập/đăng ký/OTP.
                        .requestMatchers("/", "/repository/**", "/download/**", "/view/**",
                                "/api/document-assistant/**", "/api/chat-sessions/**",
                                "/login", "/register", "/register/verify", "/register/verify/resend",
                                "/forgot-password", "/forgot-password/verify", "/forgot-password/verify/resend",
                                "/reset-password", "/access-denied", "/error/**",
                                "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        // Phân quyền theo vai trò nghiệp vụ của hệ thống EduRepo.
                        .requestMatchers("/moderation/**").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/submitter-request").hasRole("USER")
                        .requestMatchers("/reviews/**").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers("/documents/**").hasAnyRole("SUBMITTER", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .permitAll())
                .logout(logout -> logout
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) -> {
                            // API realtime/SSE cần mã 401 để client tự xử lý hết phiên,
                            // còn request HTML thông thường chuyển về trang login.
                            if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/events/")) response.setStatus(401);
                            else new org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint("/login")
                                    .commence(request, response, error);
                        })
                        .accessDeniedPage("/access-denied"))
                // PDF is embedded only by the EduRepo detail page on the same origin.
                // External websites remain unable to frame the application.
                .headers(headers -> headers
                        .frameOptions(frameOptions -> frameOptions.sameOrigin()));
        return http.build();
    }
}
