package com.stockpilot.catalog.dto;

import com.stockpilot.catalog.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Product representation payload in catalog")
public record ProductResponse(
        @Schema(description = "Internal unique identifier", example = "1")
        Long id,

        @Schema(description = "SKU Code", example = "PLC-SIEM-S71500")
        String sku,

        @Schema(description = "Commercial name", example = "Siemens SIMATIC S7-1500 CPU 1516-3 PN/DP")
        String name,

        @Schema(description = "Technical specifications")
        String technicalSpec,

        @Schema(description = "Wholesale net unit price", example = "1850000.00")
        BigDecimal unitPrice,

        @Schema(description = "Category ID", example = "1")
        Long categoryId,

        @Schema(description = "Category Name", example = "Controladores y PLCs Industriales")
        String categoryName,

        @Schema(description = "Active status in catalog", example = "true")
        boolean active,

        @Schema(description = "Entity version for optimistic concurrency control", example = "0")
        Integer version,

        @Schema(description = "Creation audit timestamp")
        Instant createdAt,

        @Schema(description = "Last update audit timestamp")
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getTechnicalSpec(),
                product.getUnitPrice(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.isActive(),
                product.getVersion(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
