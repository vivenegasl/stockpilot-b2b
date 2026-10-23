package com.stockpilot.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload for updating an existing product's metadata in catalog")
public record ProductUpdateRequest(
        @Schema(description = "Updated product commercial name", example = "Siemens SIMATIC S7-1500 CPU 1516-3 PN/DP v2")
        @NotBlank(message = "Product name is mandatory")
        @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
        String name,

        @Schema(description = "Updated engineering technical specification")
        @Size(max = 2000, message = "Technical specification cannot exceed 2000 characters")
        String technicalSpec,

        @Schema(description = "Updated unit price", example = "1920000.00")
        @NotNull(message = "Unit price is mandatory")
        @Positive(message = "Unit price must be strictly greater than zero")
        BigDecimal unitPrice,

        @Schema(description = "Category identifier", example = "1")
        @NotNull(message = "Category identifier is mandatory")
        Long categoryId,

        @Schema(description = "Active status in catalog", example = "true")
        boolean active
) {
}
