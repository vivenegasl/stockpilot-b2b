package com.stockpilot.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for creating a category")
public record CategoryCreateRequest(
        @Schema(description = "Category Name", example = "Instrumentación y Sensores")
        @NotBlank(message = "Category name is mandatory")
        @Size(min = 2, max = 100, message = "Category name must be between 2 and 100 characters")
        String name,

        @Schema(description = "Category description")
        @Size(max = 500, message = "Description cannot exceed 500 characters")
        String description
) {
}
