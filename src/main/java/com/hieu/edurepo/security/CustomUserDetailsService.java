package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final Clock clock;

    public CustomUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không đúng"));
        return CustomUserPrincipal.from(user, !user.isLoginLockedAt(LocalDateTime.now(clock)));
    }
}
