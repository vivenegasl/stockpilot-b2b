package com.stockpilot.catalog.service;

import com.stockpilot.catalog.dto.CategoryCreateRequest;
import com.stockpilot.catalog.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);

    CategoryResponse createCategory(CategoryCreateRequest request);
}
