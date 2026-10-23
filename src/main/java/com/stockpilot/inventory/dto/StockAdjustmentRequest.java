package com.stockpilot.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for adjusting physical stock level in a warehouse (US-003)")
public record StockAdjustmentRequest(
        @Schema(description = "Product SKU internal identifier", example = "1")
        @NotNull(message = "Product ID is mandatory")
        Long productId,

        @Schema(description = "Warehouse internal identifier", example = "1")
        @NotNull(message = "Warehouse ID is mandatory")
        Long warehouseId,

        @Schema(description = "Quantity delta (positive for inbound/receipt, negative for shrinkage/cycle count)", example = "15")
        @NotNull(message = "Quantity delta is mandatory")
        Integer quantityDelta,

        @Schema(description = "Auditable business reason or PO/receipt reference", example = "Recepción de Embarque Marítimo Siemens #2026-B1")
        @NotBlank(message = "Reason is mandatory")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason,

        @Schema(description = "Operator or automated system identifier performing the adjustment", example = "OPERATOR-SCL-01")
        @NotBlank(message = "Performed by is mandatory")
        @Size(max = 100, message = "Performed by cannot exceed 100 characters")
        String performedBy
) {
}
