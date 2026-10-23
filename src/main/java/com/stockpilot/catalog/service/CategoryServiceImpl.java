package com.stockpilot.catalog.service;

import com.stockpilot.catalog.domain.Category;
import com.stockpilot.catalog.dto.CategoryCreateRequest;
import com.stockpilot.catalog.dto.CategoryResponse;
import com.stockpilot.catalog.exception.CategoryNotFoundException;
import com.stockpilot.catalog.repository.CategoryRepository;
import com.stockpilot.shared.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {

    private static final Logger log = LoggerFactory.getLogger(CategoryServiceImpl.class);

    private final CategoryRepository categoryRepository;

    public CategoryServiceImpl(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Override
    public CategoryResponse getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        Objects.requireNonNull(request, "CategoryCreateRequest must not be null");

        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException(String.format("Category with name '%s' already exists.", name), "duplicate-category");
        }

        Category category = new Category(name, request.description());
        Category saved = categoryRepository.save(category);
        log.info("Registered new category: id={}, name={}", saved.getId(), saved.getName());
        return CategoryResponse.from(saved);
    }
}
