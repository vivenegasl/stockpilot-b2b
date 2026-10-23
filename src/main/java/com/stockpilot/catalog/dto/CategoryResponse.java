package com.stockpilot.catalog.dto;

import com.stockpilot.catalog.domain.Category;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Category representation payload")
public record CategoryResponse(
        @Schema(description = "Category ID", example = "1")
        Long id,

        @Schema(description = "Category Name", example = "Controladores y PLCs Industriales")
        String name,

        @Schema(description = "Category description")
        String description
) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription()
        );
    }
}
