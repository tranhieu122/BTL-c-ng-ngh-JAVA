package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class LoginAttemptService {

    private final UserRepository users;
    private final Clock clock;
    private final int maxFailures;
    private final long lockMinutes;

    public LoginAttemptService(UserRepository users,
            Clock clock,
            @Value("${app.security.login.max-failures:5}") int maxFailures,
            @Value("${app.security.login.lock-minutes:15}") long lockMinutes) {
        this.users = users;
        this.clock = clock;
        this.maxFailures = Math.max(1, maxFailures);
        this.lockMinutes = Math.max(1, lockMinutes);
    }

    @Transactional
    public void recordFailure(String email) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isBlank()) {
            return;
        }
        users.lockByEmailIgnoreCase(normalizedEmail).ifPresent(user -> applyFailure(user, LocalDateTime.now(clock)));
    }

    @Transactional
    public void recordSuccess(Long userId) {
        if (userId == null) {
            return;
        }
        users.lockById(userId).ifPresent(user -> {
            if (user.getFailedLoginAttempts() != 0 || user.getLoginLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLoginLockedUntil(null);
            }
        });
    }

    private void applyFailure(User user, LocalDateTime now) {
        if (user.isLoginLockedAt(now)) {
            return;
        }
        int currentFailures = user.getLoginLockedUntil() == null ? user.getFailedLoginAttempts() : 0;
        int nextFailures = Math.min(maxFailures, currentFailures + 1);
        user.setFailedLoginAttempts(nextFailures);
        user.setLoginLockedUntil(nextFailures >= maxFailures ? now.plusMinutes(lockMinutes) : null);
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}

