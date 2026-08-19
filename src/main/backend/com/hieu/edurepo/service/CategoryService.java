package com.hieu.edurepo.service;

import com.hieu.edurepo.entity.Category;

import java.util.List;

public interface CategoryService {
    List<Category> findAll();
    List<Category> findActive();
    Category findById(Long id);
    Category save(Category category);
    void deleteById(Long id);
}
