package com.stockpilot.inventory.dto;

import com.stockpilot.inventory.domain.Warehouse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Warehouse representation payload")
public record WarehouseResponse(
        @Schema(description = "Warehouse ID", example = "1")
        Long id,

        @Schema(description = "Unique code", example = "WH-SCL-CENTRAL")
        String code,

        @Schema(description = "Warehouse name", example = "Centro de Distribución Central Pudahuel")
        String name,

        @Schema(description = "Physical address", example = "Av. Américo Vespucio 1500, Pudahuel, Santiago, Chile")
        String address,

        @Schema(description = "Active operational status", example = "true")
        boolean active
) {
    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.isActive()
        );
    }
}
