package com.stockpilot.audit.dto;

import com.stockpilot.audit.domain.InventoryLedger;
import com.stockpilot.audit.domain.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Immutable audit ledger transaction entry")
public record LedgerEntryResponse(
        @Schema(description = "Ledger sequence ID", example = "1")
        Long id,

        @Schema(description = "Product ID", example = "1")
        Long productId,

        @Schema(description = "Warehouse ID", example = "1")
        Long warehouseId,

        @Schema(description = "Associated order ID if applicable", example = "101")
        Long orderId,

        @Schema(description = "Movement type", example = "INBOUND")
        MovementType movementType,

        @Schema(description = "Signed quantity variation (positive=in, negative=out)", example = "10")
        Integer quantityDelta,

        @Schema(description = "Post-movement snapshot of total inventory in warehouse", example = "20")
        Integer resultingTotal,

        @Schema(description = "Post-movement snapshot of reserved inventory in warehouse", example = "0")
        Integer resultingReserved,

        @Schema(description = "Audit reason or reference", example = "Importación Lote CL-2026-A")
        String reason,

        @Schema(description = "User or system identifier that triggered the operation", example = "OPERATOR-SCL-01")
        String performedBy,

        @Schema(description = "Immutable audit timestamp")
        Instant createdAt
) {
    public static LedgerEntryResponse from(InventoryLedger ledger) {
        return new LedgerEntryResponse(
                ledger.getId(),
                ledger.getProductId(),
                ledger.getWarehouseId(),
                ledger.getOrderId(),
                ledger.getMovementType(),
                ledger.getQuantityDelta(),
                ledger.getResultingTotal(),
                ledger.getResultingReserved(),
                ledger.getReason(),
                ledger.getPerformedBy(),
                ledger.getCreatedAt()
        );
    }
}
