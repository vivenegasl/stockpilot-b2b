package com.stockpilot.inventory.dto;

import com.stockpilot.inventory.domain.StockLevel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Stock level representation with segregated available inventory (US-004)")
public record StockLevelResponse(
        @Schema(description = "Stock Level ID", example = "1")
        Long id,

        @Schema(description = "Product ID", example = "1")
        Long productId,

        @Schema(description = "Product SKU", example = "PLC-SIEM-S71500")
        String productSku,

        @Schema(description = "Product Name", example = "Siemens SIMATIC S7-1500 CPU 1516-3 PN/DP")
        String productName,

        @Schema(description = "Warehouse ID", example = "1")
        Long warehouseId,

        @Schema(description = "Warehouse Code", example = "WH-SCL-CENTRAL")
        String warehouseCode,

        @Schema(description = "Warehouse Name", example = "Centro de Distribución Central Pudahuel")
        String warehouseName,

        @Schema(description = "Total physical stock on shelves", example = "20")
        Integer totalQty,

        @Schema(description = "Stock committed in active confirmed orders", example = "5")
        Integer reservedQty,

        @Schema(description = "Net stock available for new reservations (total - reserved)", example = "15")
        Integer availableQty,

        @Schema(description = "Last updated timestamp")
        Instant updatedAt
) {
    public static StockLevelResponse from(StockLevel sl) {
        return new StockLevelResponse(
                sl.getId(),
                sl.getProduct().getId(),
                sl.getProduct().getSku(),
                sl.getProduct().getName(),
                sl.getWarehouse().getId(),
                sl.getWarehouse().getCode(),
                sl.getWarehouse().getName(),
                sl.getTotalQty(),
                sl.getReservedQty(),
                sl.getAvailableQty(),
                sl.getUpdatedAt()
        );
    }
}
