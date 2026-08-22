package com.hieu.edurepo.config;

import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String userEmail;
    private final String userPassword;

    public DataInitializer(RoleRepository roleRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.admin.email:}") String adminEmail,
                           @Value("${app.admin.password:}") String adminPassword,
                           @Value("${app.user.email:}") String userEmail,
                           @Value("${app.user.password:}") String userPassword) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.userEmail = userEmail;
        this.userPassword = userPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Arrays.stream(RoleName.values()).forEach(roleName ->
                roleRepository.findByName(roleName)
                        .orElseGet(() -> roleRepository.save(new Role(roleName))));

        seedOrResetAccount(adminEmail, adminPassword, "Quản trị viên",
                new HashSet<>(roleRepository.findAll()));

        roleRepository.findByName(RoleName.USER).ifPresent(userRole ->
                seedOrResetAccount(userEmail, userPassword, "Người dùng demo", Set.of(userRole)));
    }

    private void seedOrResetAccount(String email, String password, String fullName, Set<Role> roles) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || password.isBlank()) {
            return;
        }

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElseGet(User::new);
        user.setUsername(normalizedEmail);
        user.setFullName(fullName);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));
        user.setEnabled(true);
        user.setRoles(roles);
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
