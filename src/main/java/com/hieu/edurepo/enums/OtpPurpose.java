package com.hieu.edurepo.enums;

/**
 * Mục đích sử dụng mã xác thực dùng một lần (OTP).
 */
public enum OtpPurpose {
    /** Kích hoạt và xác thực địa chỉ email khi đăng ký tài khoản mới */
    REGISTER,

    /** Xác nhận danh tính để cấp quyền đặt lại mật khẩu mới khi người dùng quên mật khẩu */
    PASSWORD_RESET
}
