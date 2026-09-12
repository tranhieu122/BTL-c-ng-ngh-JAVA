package com.hieu.edurepo.service.impl;

import com.hieu.edurepo.entity.Category;
import com.hieu.edurepo.exception.ResourceNotFoundException;
import com.hieu.edurepo.repository.CategoryRepository;
import com.hieu.edurepo.service.CategoryService;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findActive() {
        return categoryRepository.findByActiveTrueOrderByNameAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public Category findById(@NonNull Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục: " + id));
    }

    @Override
    public Category save(@NonNull Category category) {
        String normalizedName = category.getName() == null ? "" : category.getName().trim();
        category.setName(normalizedName);

        boolean nameAlreadyUsed = category.getId() == null
                ? categoryRepository.existsByNameIgnoreCase(normalizedName)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, category.getId());
        if (nameAlreadyUsed) {
            throw new IllegalStateException("CATEGORY_EXISTS");
        }
        return categoryRepository.save(category);
    }

    @Override
    public void deleteById(@NonNull Long id) {
        Category category = findById(id);
        category.setActive(false);
        categoryRepository.save(category);
    }
}
