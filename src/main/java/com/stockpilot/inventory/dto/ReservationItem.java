package com.stockpilot.inventory.dto;

import java.util.Objects;

public record ReservationItem(
        Long productId,
        int quantity
) {
    public ReservationItem {
        Objects.requireNonNull(productId, "productId must not be null");
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be strictly positive");
        }
    }
}
