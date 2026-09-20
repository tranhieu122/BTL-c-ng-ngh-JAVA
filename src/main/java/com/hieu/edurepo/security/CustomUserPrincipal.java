package com.hieu.edurepo.security;

import com.hieu.edurepo.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Collection;

/**
 * Đối tượng Principal tùy chỉnh đại diện cho người dùng đã xác thực trong Spring Security.
 *
 * <p>Bọc entity {@link User} và implement {@link UserDetails} để tích hợp với
 * Spring Security authentication pipeline. Chứa thêm các trường tiện lợi như
 * {@code id} và {@code fullName} không có trong interface chuẩn.</p>
 *
 * <p>Tạo thông qua static factory methods:</p>
 * <ul>
 *   <li>{@link #from(User)} – Tài khoản không bị khóa.</li>
 *   <li>{@link #from(User, boolean)} – Chỉ định rõ trạng thái khóa.</li>
 * </ul>
 *
 * <p>Authorities được ánh xạ từ {@link User#getRoles()} với tiền tố {@code ROLE_},
 * ví dụ: {@code RoleName.ADMIN} → {@code "ROLE_ADMIN"}.</p>
 */
public class CustomUserPrincipal implements UserDetails {

    /** ID người dùng trong CSDL (dùng để tra cứu thông tin mở rộng). */
    private final Long id;

    /** Họ và tên đầy đủ (dùng để hiển thị trên UI). */
    private final String fullName;

    /** Địa chỉ email (đóng vai trò là username trong Spring Security). */
    private final String email;

    /** Mật khẩu đã mã hóa BCrypt. */
    private final String password;

    /** Trạng thái kích hoạt tài khoản. */
    private final boolean enabled;

    /**
     * Trạng thái không bị khóa.
     * {@code false} khi {@code loginLockedUntil} còn trong tương lai.
     */
    private final boolean accountNonLocked;

    /** Danh sách quyền hạn (ROLE_ADMIN, ROLE_LIBRARIAN, v.v.). */
    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * Constructor private, chỉ dùng qua static factory methods.
     *
     * @param user             Entity người dùng.
     * @param accountNonLocked {@code false} nếu tài khoản đang bị khóa.
     */
    private CustomUserPrincipal(User user, boolean accountNonLocked) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.enabled = user.isEnabled();
        this.accountNonLocked = accountNonLocked;
        this.authorities = user.getRoles() == null ? Collections.emptyList() : user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();
    }

    /**
     * Tạo principal từ user, mặc định tài khoản không bị khóa.
     *
     * @param user Entity người dùng.
     * @return Principal đã khởi tạo.
     */
    public static CustomUserPrincipal from(User user) {
        return new CustomUserPrincipal(user, true);
    }

    /**
     * Tạo principal từ user với trạng thái khóa tài khoản được chỉ định.
     *
     * @param user             Entity người dùng.
     * @param accountNonLocked {@code true} nếu tài khoản không bị khóa.
     * @return Principal đã khởi tạo.
     */
    public static CustomUserPrincipal from(User user, boolean accountNonLocked) {
        return new CustomUserPrincipal(user, accountNonLocked);
    }

    // =========================================================================
    // Getters
    // =========================================================================

    /**
     * Trả về ID người dùng trong CSDL.
     * Đây là cách an toàn để lấy ID người dùng từ controller mà không cần
     * truy vấn lại CSDL.
     *
     * @return ID người dùng.
     */
    public Long getId() {
        return id;
    }

    /** @return Họ và tên đầy đủ. */
    public String getFullName() {
        return fullName;
    }

    /** @return Danh sách quyền hạn (GrantedAuthority). */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** @return Mật khẩu đã mã hóa. */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Trả về email làm "username" trong Spring Security.
     * EduRepo dùng email thay vì username để đăng nhập.
     *
     * @return Địa chỉ email.
     */
    @Override
    public String getUsername() {
        return email;
    }

    /** @return {@code true} nếu tài khoản đang kích hoạt. */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Kiểm tra tài khoản có đang bị khóa hay không.
     * Spring Security sẽ từ chối đăng nhập nếu trả về {@code false}.
     *
     * @return {@code true} nếu tài khoản KHÔNG bị khóa.
     */
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
}
