package com.hieu.edurepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OtpForm {
    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "\\d{6}", message = "Mã OTP gồm 6 chữ số")
    private String code;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code == null ? null : code.trim(); }
}
