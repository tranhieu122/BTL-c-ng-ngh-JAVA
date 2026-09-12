package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.User;

import java.util.List;

public interface UserService {
    List<User> findAll();
    User findById(Long id);
    User findByEmail(String email);
    User register(String fullName, String email, String password);
    User registerWithEncodedPassword(String fullName, String email, String encodedPassword);
    User save(User user, boolean encodePassword);
    void deleteById(Long id);
    void requestPasswordReset(String email);
    boolean emailExists(String email);
    boolean activeAccountExists(String email);
    void resetPassword(String email, String password);
}
