package com.stockpilot.ordering.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotNull(message = "warehouseId is required")
        Long warehouseId,

        @NotEmpty(message = "items list cannot be empty")
        @Valid
        List<OrderItemRequest> items,

        @Size(max = 500, message = "notes cannot exceed 500 characters")
        String notes
) {}
