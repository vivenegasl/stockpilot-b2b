package com.stockpilot.catalog.exception;

import com.stockpilot.shared.exception.ConflictException;

public class DuplicateSkuException extends ConflictException {

    public DuplicateSkuException(String sku) {
        super(String.format("Product SKU '%s' already exists in catalog.", sku), "duplicate-sku");
    }
}
