package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + email));
    }

    @Override
    public User register(String fullName, String email, String password) {
        String normalizedEmail = normalizeRequiredText(email, "Email không được để trống").toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("EMAIL_EXISTS");
        }
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Chưa khởi tạo vai trò USER"));
        User user = new User();
        user.setFullName(normalizeRequiredText(fullName, "Họ tên không được để trống"));
        user.setEmail(normalizedEmail);
        // Email đã unique; dùng chính email làm username để tránh trùng phần trước dấu @.
        user.setUsername(normalizedEmail);
        user.setPassword(passwordEncoder.encode(normalizeRequiredText(password, "Mật khẩu không được để trống")));
        user.getRoles().add(userRole);
        return userRepository.save(user);
    }

    @Override
    public User save(User user, boolean encodePassword) {
        if ((user.getUsername() == null || user.getUsername().isBlank()) && user.getEmail() != null) {
            user.setUsername(usernameFromEmail(user.getEmail()));
        }
        if (encodePassword) {
            user.setPassword(passwordEncoder.encode(
                    normalizeRequiredText(user.getPassword(), "Mật khẩu không được để trống")));
        }
        return userRepository.save(user);
    }

    @Override
    public void deleteById(Long id) {
        userRepository.delete(findById(id));
    }

    private String usernameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String normalizeRequiredText(String value, String message) {
        String text = Objects.requireNonNull(value, message).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
