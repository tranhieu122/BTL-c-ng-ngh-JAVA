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

/**
 * Dịch vụ nạp dữ liệu xác thực người dùng cho Spring Security (UserDetailsService).
 * <p>
 * Thực hiện tìm kiếm thông tin tài khoản theo email (chuẩn hóa chữ thường) và kiểm tra trạng thái khóa tài khoản.
 * </p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    /** Kho dữ liệu người dùng */
    private final UserRepository userRepository;

    /** Đồng hồ hệ thống để xác thực thời gian khóa tài khoản chính xác */
    private final Clock clock;

    public CustomUserDetailsService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /**
     * Nạp thông tin người dùng từ cơ sở dữ liệu thông qua địa chỉ email đăng nhập.
     *
     * @param email Địa chỉ email người dùng nhập vào form đăng nhập
     * @return Đối tượng UserDetails chứa thông tin tài khoản, mật khẩu băm và danh sách quyền hạn
     * @throws UsernameNotFoundException Ném ra nếu không tìm thấy email tương ứng trong hệ thống
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Chuẩn hóa email: cắt khoảng trắng và đưa về chữ thường
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        // Tìm kiếm người dùng không phân biệt chữ hoa thường trong CSDL
        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Email hoặc mật khẩu không đúng"));

        // Kiểm tra xem tài khoản có đang trong thời gian bị khóa tạm thời do nhập sai quá số lần không
        boolean accountNonLocked = !user.isLoginLockedAt(LocalDateTime.now(clock));

        // Đóng gói thực thể User thành CustomUserPrincipal cung cấp cho Spring Security Context
        return CustomUserPrincipal.from(user, accountNonLocked);
    }
}
