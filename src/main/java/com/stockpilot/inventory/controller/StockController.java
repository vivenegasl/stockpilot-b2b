package com.stockpilot.inventory.controller;

import com.stockpilot.inventory.dto.StockAdjustmentRequest;
import com.stockpilot.inventory.dto.StockLevelResponse;
import com.stockpilot.inventory.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stock")
@Tag(name = "Stock & Inventory", description = "Operations for stock management, availability, and physical adjustments (US-003 & US-004)")
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping(value = "/adjust", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute an atomic physical stock adjustment (Inbound / Cycle Count) with immutable ledger entry (US-003)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock adjusted successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed or negative adjustment exceeds available stock",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Product or Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Lock acquisition timeout under high contention",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<StockLevelResponse> adjustStock(@Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(stockService.adjustStock(request));
    }

    @GetMapping(value = "/product/{productId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query stock availability across all active regional warehouses for a product (US-004)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of warehouse stock levels for product"),
            @ApiResponse(responseCode = "404", description = "Product not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<List<StockLevelResponse>> getStockByProduct(@PathVariable Long productId) {
        return ResponseEntity.ok(stockService.getStockByProduct(productId));
    }

    @GetMapping(value = "/warehouse/{warehouseId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Query paginated catalog inventory stored inside a specific warehouse (US-004)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated stock levels for warehouse"),
            @ApiResponse(responseCode = "404", description = "Warehouse not found",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<Page<StockLevelResponse>> getStockByWarehouse(
            @PathVariable Long warehouseId,
            @PageableDefault(size = 20, sort = "product.sku", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return ResponseEntity.ok(stockService.getStockByWarehouse(warehouseId, pageable));
    }
}
