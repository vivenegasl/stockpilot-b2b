package com.stockpilot.ordering.exception;

import com.stockpilot.shared.exception.ResourceNotFoundException;

public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(Long id) {
        super(String.format("PurchaseOrder with ID '%d' was not found.", id));
    }

    public OrderNotFoundException(String orderNumber) {
        super(String.format("PurchaseOrder with number '%s' was not found.", orderNumber));
    }
}
