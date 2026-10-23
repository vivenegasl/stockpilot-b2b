package com.stockpilot.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a regional physical warehouse")
public record WarehouseCreateRequest(
        @Schema(description = "Unique warehouse code", example = "WH-SCL-CENTRAL")
        @NotBlank(message = "Warehouse code is mandatory")
        @Size(min = 3, max = 20, message = "Warehouse code must be between 3 and 20 characters")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code must contain only alphanumeric characters, dashes, and underscores")
        String code,

        @Schema(description = "Warehouse name", example = "Centro de Distribución Central Pudahuel")
        @NotBlank(message = "Warehouse name is mandatory")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Schema(description = "Physical geographical address", example = "Av. Américo Vespucio 1500, Pudahuel, Santiago, Chile")
        @NotBlank(message = "Address is mandatory")
        @Size(min = 5, max = 500, message = "Address must be between 5 and 500 characters")
        String address
) {
}
