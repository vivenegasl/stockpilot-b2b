package com.stockpilot.audit.service;

import com.stockpilot.audit.domain.InventoryLedger;
import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.dto.LedgerEntryResponse;
import com.stockpilot.audit.repository.InventoryLedgerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final InventoryLedgerRepository ledgerRepository;

    public LedgerServiceImpl(InventoryLedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Override
    @Transactional
    public void recordMovement(
            Long productId,
            Long warehouseId,
            Long orderId,
            MovementType movementType,
            int quantityDelta,
            int resultingTotal,
            int resultingReserved,
            String reason,
            String performedBy
    ) {
        InventoryLedger entry = new InventoryLedger(
                productId,
                warehouseId,
                orderId,
                movementType,
                quantityDelta,
                resultingTotal,
                resultingReserved,
                reason,
                performedBy
        );

        ledgerRepository.save(entry);
        log.info("Ledger entry appended: type={}, productId={}, warehouseId={}, delta={}, resultingTotal={}",
                movementType, productId, warehouseId, quantityDelta, resultingTotal);
    }

    @Override
    public Page<LedgerEntryResponse> getLedgerEntries(Long productId, Long warehouseId, Pageable pageable) {
        return ledgerRepository.findLedgerEntries(productId, warehouseId, pageable)
                .map(LedgerEntryResponse::from);
    }
}
