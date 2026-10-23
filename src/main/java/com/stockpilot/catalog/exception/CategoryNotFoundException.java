package com.stockpilot.catalog.exception;

import com.stockpilot.shared.exception.ResourceNotFoundException;

public class CategoryNotFoundException extends ResourceNotFoundException {

    public CategoryNotFoundException(Long id) {
        super("Category", id);
    }
}
