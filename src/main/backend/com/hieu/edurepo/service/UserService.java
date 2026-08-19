package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.User;

import java.util.List;

public interface UserService {
    List<User> findAll();
    User findById(Long id);
    User findByEmail(String email);
    User register(String fullName, String email, String password);
    User save(User user, boolean encodePassword);
    void deleteById(Long id);
}
