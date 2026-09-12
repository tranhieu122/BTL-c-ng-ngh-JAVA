package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.entity.Role;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.ApprovalHistoryRepository;
import com.hieu.edurepo.repository.DocumentRepository;
import com.hieu.edurepo.repository.RoleRepository;
import com.hieu.edurepo.repository.UserRepository;
import com.hieu.edurepo.service.UserService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final DocumentRepository documentRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository, DocumentRepository documentRepository,
                           ApprovalHistoryRepository approvalHistoryRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.documentRepository = documentRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findByDeletedAtIsNull();
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .filter(user -> user.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản: " + normalizedEmail));
    }

    @Override
    public User register(String fullName, String email, String password) {
        return createUser(fullName, email, passwordEncoder.encode(requirePassword(password)));
    }

    @Override
    public User registerWithEncodedPassword(String fullName, String email, String encodedPassword) {
        if (encodedPassword == null || encodedPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu không hợp lệ");
        }
        return createUser(fullName, email, encodedPassword);
    }

    private User createUser(String fullName, String email, String encodedPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("EMAIL_EXISTS");
        }
        Role userRole = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("Chưa khởi tạo vai trò USER"));
        User user = new User();
        user.setFullName(normalizeRequiredText(fullName, "Họ tên không được để trống"));
        user.setEmail(normalizedEmail);
        // Email đã unique; dùng chính email làm username để tránh trùng phần trước dấu @.
        user.setUsername(normalizedEmail);
        user.setPassword(encodedPassword);
        user.getRoles().add(userRole);
        return persistUser(user);
    }

    @Override
    public User save(User user, boolean encodePassword) {
        roleRepository.lockByName(RoleName.ADMIN);
        String normalizedEmail = normalizeEmail(user.getEmail());
        Long accountId = user.getId();
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !Objects.equals(existing.getId(), accountId))
                .ifPresent(existing -> { throw new IllegalStateException("EMAIL_EXISTS"); });
        if (user.getId() != null) {
            User current = userRepository.lockById(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
            if (current.getDeletedAt() != null) throw new ResourceNotFoundException("Tài khoản đã được xóa.");
            // Merge only admin-editable fields; a detached admin form must not overwrite a newer avatar/profile.
            current.setFullName(user.getFullName()); current.setEmail(user.getEmail());
            current.setEnabled(user.isEnabled()); current.setRoles(user.getRoles());
            if (encodePassword) current.setPassword(user.getPassword());
            user = current;
        }
        user.setFullName(normalizeRequiredText(user.getFullName(), "Họ tên không được để trống"));
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedEmail);
        if (encodePassword) {
            user.setPassword(passwordEncoder.encode(requirePassword(user.getPassword())));
            user.setPasswordResetRequestedAt(null);
            user.setFailedLoginAttempts(0);
            user.setLoginLockedUntil(null);
        }
        return persistUser(user);
    }

    @Override
    public void deleteById(Long id) {
        roleRepository.lockByName(RoleName.ADMIN);
        User user = findById(id);
        if (documentRepository.existsByCreatedById(id)
                || approvalHistoryRepository.existsByReviewerId(id)) {
            throw new IllegalStateException("USER_IN_USE");
        }
        userRepository.delete(user);
        userRepository.flush();
    }

    private User persistUser(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("EMAIL_EXISTS", exception);
        }
    }

    private String normalizeEmail(String email) {
        return normalizeRequiredText(email, "Email không được để trống").toLowerCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String message) {
        String text = Objects.requireNonNull(value, message).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private String requirePassword(String password) {
        if (!com.hieu.edurepo.util.PasswordPolicy.isValid(password)) {
            throw new IllegalArgumentException(com.hieu.edurepo.util.PasswordPolicy.MESSAGE);
        }
        return password;
    }

    @Override
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank() || email.length() > 255) return;
        userRepository.requestPasswordReset(email.trim().toLowerCase(Locale.ROOT), java.time.LocalDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        if (email == null || email.isBlank() || email.length() > 255) return false;
        return userRepository.existsByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean activeAccountExists(String email) {
        if (email == null || email.isBlank() || email.length() > 255) return false;
        return userRepository.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .filter(user -> user.getDeletedAt() == null)
                .isPresent();
    }

    @Override
    public void resetPassword(String email, String password) {
        User user = userRepository.lockByEmailIgnoreCase(normalizeEmail(email))
                .filter(existing -> existing.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
        user.setPassword(passwordEncoder.encode(requirePassword(password)));
        user.setPasswordResetRequestedAt(null);
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        userRepository.saveAndFlush(user);
    }
}
