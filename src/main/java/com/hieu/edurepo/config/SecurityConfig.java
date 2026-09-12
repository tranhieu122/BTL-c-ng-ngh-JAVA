package com.hieu.edurepo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
                .addFilterBefore(new com.hieu.edurepo.security.AccountSessionFilter(users),
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // The initial SSE request is authenticated; completion may happen after logout.
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/", "/repository/**", "/download/**", "/view/**",
                                "/login", "/register", "/register/verify", "/register/verify/resend",
                                "/forgot-password", "/forgot-password/verify", "/forgot-password/verify/resend",
                                "/reset-password", "/access-denied", "/error/**",
                                "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/moderation/**").hasAnyRole("REVIEWER", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
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
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .permitAll())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, error) -> {
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
