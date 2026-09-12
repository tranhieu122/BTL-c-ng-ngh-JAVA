package com.hieu.edurepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Deliberately excludes identity, role, password and storage fields. */
public class ProfileForm {
    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự.")
    private String fullName;
    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự.")
    @Pattern(regexp = "[0-9+() .-]*", message = "Số điện thoại không hợp lệ.")
    private String phoneNumber;
    @Size(max = 150, message = "Đơn vị tối đa 150 ký tự.")
    private String affiliation;
    @Size(max = 1000, message = "Giới thiệu tối đa 1000 ký tự.")
    private String bio;
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String value) { phoneNumber = value; }
    public String getAffiliation() { return affiliation; }
    public void setAffiliation(String value) { affiliation = value; }
    public String getBio() { return bio; }
    public void setBio(String value) { bio = value; }
}
