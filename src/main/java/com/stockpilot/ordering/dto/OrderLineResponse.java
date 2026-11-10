package com.stockpilot.ordering.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        Long id,
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {}
