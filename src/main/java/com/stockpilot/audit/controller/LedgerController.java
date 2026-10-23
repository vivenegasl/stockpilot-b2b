package com.stockpilot.audit.controller;

import com.stockpilot.audit.dto.LedgerEntryResponse;
import com.stockpilot.audit.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger")
@Tag(name = "Inventory Audit Ledger", description = "Immutable append-only audit trail of stock movements (US-005)")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Retrieve chronological audit movements with optional product and warehouse filters (US-005)")
    @ApiResponse(responseCode = "200", description = "Paginated ledger entries")
    public ResponseEntity<Page<LedgerEntryResponse>> getLedgerEntries(
            @Parameter(description = "Product ID filter")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "Warehouse ID filter")
            @RequestParam(required = false) Long warehouseId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<LedgerEntryResponse> page = ledgerService.getLedgerEntries(productId, warehouseId, pageable);
        return ResponseEntity.ok(page);
    }
}
