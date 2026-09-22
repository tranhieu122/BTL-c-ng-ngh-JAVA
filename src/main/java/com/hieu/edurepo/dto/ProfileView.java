package com.hieu.edurepo.dto;

import com.hieu.edurepo.entity.User;

/**
 * DTO chứa toàn bộ thông tin hiển thị hồ sơ cá nhân của người học trên giao diện.
 */
public record ProfileView(String fullName, String email, String phoneNumber,
                          String affiliation, String bio, String avatarRevision) {
    public static ProfileView from(User user) {
        return new ProfileView(user.getFullName(), user.getEmail(), user.getPhoneNumber(),
                user.getAffiliation(), user.getBio(), user.getAvatarKey() == null ? "default" : user.getAvatarKey());
    }
    public ProfileForm form() {
        var form = new ProfileForm();
        form.setFullName(fullName); form.setPhoneNumber(phoneNumber);
        form.setAffiliation(affiliation); form.setBio(bio);
        return form;
    }
}
