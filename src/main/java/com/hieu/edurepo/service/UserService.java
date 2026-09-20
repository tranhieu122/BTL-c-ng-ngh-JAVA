package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.User;

import java.util.List;

/**
 * Service interface quản lý người dùng trong hệ thống EduRepo.
 *
 * <p>Cung cấp các nghiệp vụ: tìm kiếm, đăng ký, cập nhật, xóa tài khoản,
 * và quản lý quy trình đặt lại mật khẩu qua email/OTP.</p>
 *
 * <p>Implementation: {@code UserServiceImpl}.</p>
 */
public interface UserService {

    /**
     * Lấy danh sách tất cả người dùng trong hệ thống.
     *
     * @return Danh sách tất cả {@link User}.
     */
    List<User> findAll();

    /**
     * Tìm người dùng theo ID. Ném {@code ResourceNotFoundException} nếu không tồn tại.
     *
     * @param id ID người dùng.
     * @return Đối tượng {@link User}.
     */
    User findById(Long id);

    /**
     * Tìm người dùng theo địa chỉ email.
     *
     * @param email Địa chỉ email cần tìm.
     * @return Đối tượng {@link User} hoặc {@code null} nếu không tìm thấy.
     */
    User findByEmail(String email);

    /**
     * Đăng ký tài khoản mới với mật khẩu plain-text (sẽ được mã hóa bên trong).
     *
     * @param fullName Họ tên đầy đủ.
     * @param email    Địa chỉ email (duy nhất).
     * @param password Mật khẩu plain-text.
     * @return Tài khoản đã được tạo.
     * @throws IllegalStateException Nếu email đã tồn tại ({@code "EMAIL_EXISTS"}).
     */
    User register(String fullName, String email, String password);

    /**
     * Đăng ký tài khoản với mật khẩu đã được mã hóa sẵn (dùng trong luồng OTP).
     * Mật khẩu không được mã hóa lại bên trong phương thức này.
     *
     * @param fullName       Họ tên đầy đủ.
     * @param email          Địa chỉ email (duy nhất).
     * @param encodedPassword Mật khẩu đã mã hóa BCrypt.
     * @return Tài khoản đã được tạo.
     * @throws IllegalStateException Nếu email đã tồn tại ({@code "EMAIL_EXISTS"}).
     */
    User registerWithEncodedPassword(String fullName, String email, String encodedPassword);

    /**
     * Lưu thông tin người dùng, tuỳ chọn có mã hóa mật khẩu hay không.
     *
     * @param user           Đối tượng người dùng cần lưu.
     * @param encodePassword {@code true} nếu cần mã hóa trường password trước khi lưu.
     * @return Người dùng sau khi lưu.
     */
    User save(User user, boolean encodePassword);

    /**
     * Xóa mềm hoặc xóa cứng tài khoản theo ID.
     *
     * @param id ID tài khoản cần xóa.
     */
    void deleteById(Long id);

    /**
     * Ghi nhận yêu cầu đặt lại mật khẩu (cập nhật trường {@code passwordResetRequestedAt}).
     * Dùng để giới hạn tần suất gửi email reset.
     *
     * @param email Email tài khoản cần đặt lại mật khẩu.
     */
    void requestPasswordReset(String email);

    /**
     * Kiểm tra email đã được đăng ký trong hệ thống chưa (kể cả tài khoản bị vô hiệu hóa).
     *
     * @param email Địa chỉ email cần kiểm tra.
     * @return {@code true} nếu email đã tồn tại.
     */
    boolean emailExists(String email);

    /**
     * Kiểm tra tài khoản đang hoạt động (enabled) với email cho trước có tồn tại không.
     * Dùng trong luồng quên mật khẩu (không gửi OTP cho tài khoản bị vô hiệu hóa).
     *
     * @param email Địa chỉ email cần kiểm tra.
     * @return {@code true} nếu tài khoản hoạt động tồn tại.
     */
    boolean activeAccountExists(String email);

    /**
     * Đặt lại mật khẩu mới cho tài khoản (mật khẩu sẽ được mã hóa BCrypt).
     *
     * @param email    Email tài khoản cần đặt lại.
     * @param password Mật khẩu mới (plain-text).
     */
    void resetPassword(String email, String password);
}
