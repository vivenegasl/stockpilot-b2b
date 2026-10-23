package com.stockpilot.inventory.exception;

import com.stockpilot.shared.exception.ResourceNotFoundException;

public class WarehouseNotFoundException extends ResourceNotFoundException {

    public WarehouseNotFoundException(Long id) {
        super("Warehouse", id);
    }

    public WarehouseNotFoundException(String code) {
        super(String.format("Warehouse with code '%s' was not found.", code));
    }
}
