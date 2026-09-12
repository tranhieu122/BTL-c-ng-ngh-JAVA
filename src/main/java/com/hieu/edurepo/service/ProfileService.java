package com.hieu.edurepo.service;

import com.hieu.edurepo.dto.ProfileForm;
import com.hieu.edurepo.dto.ProfileView;
import com.hieu.edurepo.entity.User;
import com.hieu.edurepo.enums.RoleName;
import com.hieu.edurepo.repository.*;
import com.hieu.edurepo.util.PasswordPolicy;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Service
@Transactional
public class ProfileService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final BookmarkRepository bookmarks;
    private final DocumentCollectionRepository collections;
    private final CollectionItemRepository items;
    private final PasswordEncoder encoder;
    private final AvatarStorageService avatars;
    private final Validator validator;
    public ProfileService(UserRepository users, RoleRepository roles, BookmarkRepository bookmarks,
                          DocumentCollectionRepository collections, CollectionItemRepository items,
                          PasswordEncoder encoder, AvatarStorageService avatars, Validator validator) {
        this.users = users; this.roles = roles; this.bookmarks = bookmarks;
        this.collections = collections; this.items = items; this.encoder = encoder;
        this.avatars = avatars; this.validator = validator;
    }
    private User active(User user) {
        if (!user.isEnabled() || user.getDeletedAt() != null) throw new AccessDeniedException("Tài khoản không còn hoạt động.");
        return user;
    }
    private User locked(Long id) {
        return active(users.lockById(id).orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản.")));
    }
    @Transactional(readOnly = true)
    public ProfileView get(Long id) {
        return ProfileView.from(active(users.findById(id).orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản."))));
    }
    public void update(Long id, ProfileForm form) {
        var violations = validator.validate(form);
        if (!violations.isEmpty()) throw new IllegalArgumentException(violations.iterator().next().getMessage());
        User account = locked(id);
        account.setFullName(clean(form.getFullName())); account.setPhoneNumber(clean(form.getPhoneNumber()));
        account.setAffiliation(clean(form.getAffiliation())); account.setBio(clean(form.getBio()));
    }
    private String clean(String value) { return value == null ? "" : value.strip(); }
    private void verify(User account, String password) {
        if (password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !encoder.matches(password, account.getPassword()))
            throw new IllegalArgumentException("Mật khẩu hiện tại không đúng.");
    }
    public void changePassword(Long id, String current, String next, String confirmation) {
        User account = locked(id);
        verify(account, current);
        if (!PasswordPolicy.isValid(next)) throw new IllegalArgumentException(PasswordPolicy.MESSAGE);
        if (!next.equals(confirmation)) throw new IllegalArgumentException("Xác nhận mật khẩu mới không khớp.");
        if (encoder.matches(next, account.getPassword())) throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại.");
        account.setPassword(encoder.encode(next)); account.setPasswordResetRequestedAt(null);
    }
    public void uploadAvatar(Long id, MultipartFile file) {
        User account = locked(id);
        String old = account.getAvatarKey();
        String key = avatars.store(id, file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                avatars.delete(id, status == STATUS_COMMITTED ? old : key);
            }
        });
        account.setAvatarKey(key);
    }
    public void removeAvatar(Long id) {
        User account = locked(id);
        removeAfterCommit(id, account.getAvatarKey()); account.setAvatarKey(null);
    }
    private void removeAfterCommit(Long id, String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { avatars.delete(id, key); }
        });
    }
    public void deleteAccount(Long id, String password, String confirmation, boolean acknowledged) {
        // Serialize self-deletions before locking individual users, including simultaneous admins.
        roles.lockByName(RoleName.ADMIN).orElseThrow();
        User account = locked(id);
        verify(account, password);
        if (!acknowledged || !"XÓA TÀI KHOẢN".equals(confirmation))
            throw new IllegalArgumentException("Vui lòng xác nhận cảnh báo và nhập chính xác XÓA TÀI KHOẢN.");
        if (account.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN)
                && users.countActiveRole(RoleName.ADMIN) <= 1)
            throw new IllegalArgumentException("Không thể xóa quản trị viên hoạt động cuối cùng. Hãy bổ sung quản trị viên trước.");
        bookmarks.deleteByUserId(id);
        for (var collection : collections.findByOwnerIdOrderByUpdatedAtDesc(id)) {
            items.deleteByCollectionId(collection.getId());
            collections.delete(collection);
        }
        removeAfterCommit(id, account.getAvatarKey());
        account.setAvatarKey(null); account.setFullName("Tài khoản đã xóa");
        account.setPhoneNumber(null); account.setAffiliation(null); account.setBio(null);
        String tombstone = "deleted-" + UUID.randomUUID();
        account.setEmail(tombstone + "@deleted.invalid"); account.setUsername(tombstone);
        account.setPassword(encoder.encode(UUID.randomUUID().toString()));
        account.setEnabled(false); account.getRoles().clear(); account.setPasswordResetRequestedAt(null);
        account.setDeletedAt(java.time.LocalDateTime.now());
        users.flush();
    }
}
