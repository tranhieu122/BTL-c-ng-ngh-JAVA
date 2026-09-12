package com.hieu.edurepo.util;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final String MESSAGE = "Mật khẩu phải có ít nhất 8 ký tự và không vượt quá 72 byte UTF-8";

    private PasswordPolicy() { }

    public static boolean isValid(String password) {
        return password != null && !password.isBlank() && password.length() >= 8
                && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
