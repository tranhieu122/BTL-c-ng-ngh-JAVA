package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Category;

import org.springframework.lang.NonNull;

import java.util.List;

public interface CategoryService {
    List<Category> findAll();
    List<Category> findActive();
    Category findById(@NonNull Long id);
    Category save(@NonNull Category category);
    void deleteById(@NonNull Long id);
}
