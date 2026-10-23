package com.stockpilot.catalog.exception;

import com.stockpilot.shared.exception.ResourceNotFoundException;

public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(Long id) {
        super("Product", id);
    }

    public ProductNotFoundException(String sku) {
        super(String.format("Product with SKU '%s' was not found in catalog.", sku));
    }
}
