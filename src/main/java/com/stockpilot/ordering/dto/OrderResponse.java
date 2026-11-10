package com.stockpilot.ordering.dto;

import com.stockpilot.ordering.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        Long id,
        String orderNumber,
        UUID idempotencyKey,
        OrderStatus status,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        BigDecimal totalNetAmount,
        String notes,
        Integer version,
        List<OrderLineResponse> lines,
        Instant createdAt,
        Instant updatedAt
) {}
