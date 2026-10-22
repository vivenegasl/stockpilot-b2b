package com.stockpilot.shared.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {

    public InsufficientStockException(String sku, String warehouseCode, int requested, int available) {
        super(String.format("Insufficient stock for SKU '%s' at warehouse '%s': requested %d, available %d.",
                        sku, warehouseCode, requested, available),
                HttpStatus.CONFLICT,
                "insufficient-stock");
    }
}
