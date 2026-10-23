package com.stockpilot.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "Payload for creating a new industrial product SKU in catalog")
public record ProductCreateRequest(
        @Schema(description = "Unique SKU code", example = "PLC-SIEM-S71500")
        @NotBlank(message = "SKU code is mandatory")
        @Size(min = 3, max = 50, message = "SKU must be between 3 and 50 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "SKU must only contain alphanumeric characters, underscores, and dashes")
        String sku,

        @Schema(description = "Product commercial name", example = "Siemens SIMATIC S7-1500 CPU 1516-3 PN/DP")
        @NotBlank(message = "Product name is mandatory")
        @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
        String name,

        @Schema(description = "Engineering and technical specification", example = "1 MB work memory, integrated PROFINET interface")
        @Size(max = 2000, message = "Technical specification cannot exceed 2000 characters")
        String technicalSpec,

        @Schema(description = "Wholesale net unit price (positive value)", example = "1850000.00")
        @NotNull(message = "Unit price is mandatory")
        @Positive(message = "Unit price must be strictly greater than zero")
        BigDecimal unitPrice,

        @Schema(description = "Associated category identifier", example = "1")
        @NotNull(message = "Category identifier is mandatory")
        Long categoryId
) {
}
