package com.stockpilot.audit.service;

import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.dto.LedgerEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LedgerService {

    void recordMovement(
            Long productId,
            Long warehouseId,
            Long orderId,
            MovementType movementType,
            int quantityDelta,
            int resultingTotal,
            int resultingReserved,
            String reason,
            String performedBy
    );

    Page<LedgerEntryResponse> getLedgerEntries(Long productId, Long warehouseId, Pageable pageable);
}
