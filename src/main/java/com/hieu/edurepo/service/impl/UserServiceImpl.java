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
import com.hieu.edurepo.service.RealtimeChangeTracker;
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
    private final RealtimeChangeTracker realtimeChanges;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           RoleRepository roleRepository, DocumentRepository documentRepository,
                           ApprovalHistoryRepository approvalHistoryRepository,
                           RealtimeChangeTracker realtimeChanges) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.documentRepository = documentRepository;
        this.approvalHistoryRepository = approvalHistoryRepository;
        this.realtimeChanges = realtimeChanges;
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
        // Luồng đăng ký thông thường nhận mật khẩu gốc nên phải validate rồi BCrypt trước khi lưu.
        return createUser(fullName, email, passwordEncoder.encode(requirePassword(password)));
    }

    @Override
    public User registerWithEncodedPassword(String fullName, String email, String encodedPassword) {
        // Luồng OTP đã mã hóa mật khẩu trước khi đưa vào session, nên không encode lần hai ở đây.
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
        // Khóa role ADMIN để tránh hai thao tác quản trị đồng thời làm mất admin cuối cùng.
        roleRepository.lockByName(RoleName.ADMIN);
        String normalizedEmail = normalizeEmail(user.getEmail());
        Long accountId = user.getId();
        userRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> !Objects.equals(existing.getId(), accountId))
                .ifPresent(existing -> { throw new IllegalStateException("EMAIL_EXISTS"); });
        if (user.getId() != null) {
            User current = userRepository.lockById(user.getId()).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
            if (current.getDeletedAt() != null) throw new ResourceNotFoundException("Tài khoản đã được xóa.");
            // Chỉ merge các trường admin được phép sửa; form detached không được ghi đè avatar/hồ sơ mới hơn.
            current.setFullName(user.getFullName()); current.setEmail(user.getEmail());
            current.setEnabled(user.isEnabled()); current.setRoles(user.getRoles());
            if (encodePassword) current.setPassword(user.getPassword());
            user = current;
        }
        user.setFullName(normalizeRequiredText(user.getFullName(), "Họ tên không được để trống"));
        user.setEmail(normalizedEmail);
        user.setUsername(normalizedEmail);
        if (encodePassword) {
            // Đổi mật khẩu qua admin cũng mở khóa đăng nhập và xóa cờ yêu cầu reset.
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
        // Không xóa cứng tài khoản đã có tài liệu hoặc lịch sử duyệt để bảo toàn audit trail.
        if (documentRepository.existsByCreatedById(id)
                || approvalHistoryRepository.existsByReviewerId(id)) {
            throw new IllegalStateException("USER_IN_USE");
        }
        userRepository.delete(user);
        userRepository.flush();
        realtimeChanges.changedForUserAfterCommit(id);
    }

    private User persistUser(User user) {
        try {
            User saved = userRepository.saveAndFlush(user);
            realtimeChanges.changedForUserAfterCommit(saved.getId());
            return saved;
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
        // Hàm này cố ý im lặng với email không hợp lệ để controller không làm lộ tài khoản tồn tại hay không.
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
        // Khóa tài khoản theo email trong lúc đổi mật khẩu để tránh ghi đè khi có request reset đồng thời.
        User user = userRepository.lockByEmailIgnoreCase(normalizeEmail(email))
                .filter(existing -> existing.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản."));
        user.setPassword(passwordEncoder.encode(requirePassword(password)));
        user.setPasswordResetRequestedAt(null);
        user.setFailedLoginAttempts(0);
        user.setLoginLockedUntil(null);
        userRepository.saveAndFlush(user);
        realtimeChanges.changedForUserAfterCommit(user.getId());
    }
}
