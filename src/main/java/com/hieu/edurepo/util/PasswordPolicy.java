package com.hieu.edurepo.util;

import java.nio.charset.StandardCharsets;

/**
 * Lớp tiện ích định nghĩa và kiểm tra chính sách mật khẩu của hệ thống EduRepo.
 *
 * <p>Quy tắc mật khẩu hiện tại:</p>
 * <ul>
 *   <li>Tối thiểu 8 ký tự (bảo vệ chống brute-force).</li>
 *   <li>Tối đa 72 byte UTF-8 (giới hạn của BCrypt – chuỗi dài hơn bị cắt bớt silently).</li>
 * </ul>
 *
 * <p>Lý do giới hạn 72 byte: BCrypt chỉ xử lý 72 byte đầu tiên. Cho phép mật khẩu
 * dài hơn sẽ tạo ra cảm giác bảo mật giả – hai mật khẩu chỉ khác nhau ở byte thứ 73+
 * sẽ được BCrypt coi là giống nhau.</p>
 *
 * <p>Thông báo lỗi được lưu trong hằng số {@link #MESSAGE} để đồng bộ giữa
 * backend validation và frontend display.</p>
 *
 * <p>Đây là lớp utility tĩnh, không thể khởi tạo trực tiếp.</p>
 */
public final class PasswordPolicy {

    /**
     * Thông báo lỗi hiển thị cho người dùng khi mật khẩu không thỏa mãn chính sách.
     */
    public static final String MESSAGE = "Mật khẩu phải có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8";

    /** Private constructor: lớp utility không được khởi tạo. */
    private PasswordPolicy() { }

    /**
     * Kiểm tra mật khẩu có thỏa mãn chính sách không.
     *
     * @param password Mật khẩu cần kiểm tra (plain-text, chưa mã hóa).
     * @return {@code true} nếu mật khẩu không null, không trống, có ít nhất 8 ký tự,
     *         và kích thước UTF-8 không vượt quá 72 byte.
     */
    public static boolean isValid(String password) {
        return password != null && !password.isBlank() && password.length() >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
