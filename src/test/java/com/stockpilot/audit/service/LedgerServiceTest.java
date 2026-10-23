package com.stockpilot.audit.service;

import com.stockpilot.audit.domain.InventoryLedger;
import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.dto.LedgerEntryResponse;
import com.stockpilot.audit.repository.InventoryLedgerRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private InventoryLedgerRepository ledgerRepository;

    @InjectMocks
    private LedgerServiceImpl ledgerService;

    @Test
    @DisplayName("US-005: Appends immutable inventory ledger movement successfully")
    void recordMovement_appendsEntry() {
        ledgerService.recordMovement(
                1L, 2L, 100L,
                MovementType.INBOUND, 10,
                20, 0,
                "Lote inicial", "OPERATOR-01"
        );

        ArgumentCaptor<InventoryLedger> captor = ArgumentCaptor.forClass(InventoryLedger.class);
        verify(ledgerRepository).save(captor.capture());

        InventoryLedger saved = captor.getValue();
        assertThat(saved.getProductId()).isEqualTo(1L);
        assertThat(saved.getWarehouseId()).isEqualTo(2L);
        assertThat(saved.getOrderId()).isEqualTo(100L);
        assertThat(saved.getMovementType()).isEqualTo(MovementType.INBOUND);
        assertThat(saved.getQuantityDelta()).isEqualTo(10);
        assertThat(saved.getResultingTotal()).isEqualTo(20);
        assertThat(saved.getResultingReserved()).isZero();
        assertThat(saved.getReason()).isEqualTo("Lote inicial");
        assertThat(saved.getPerformedBy()).isEqualTo("OPERATOR-01");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("US-005: Retrieves paginated chronological audit entries")
    void getLedgerEntries_returnsPaginatedEntries() {
        InventoryLedger entry = new InventoryLedger(
                1L, 2L, null, MovementType.ADJUSTMENT, -2, 18, 0, "Ajuste merma", "SUPERVISOR-01"
        );
        Pageable pageable = PageRequest.of(0, 10);
        when(ledgerRepository.findLedgerEntries(eq(1L), eq(2L), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(entry), pageable, 1));

        Page<LedgerEntryResponse> result = ledgerService.getLedgerEntries(1L, 2L, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().movementType()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(result.getContent().getFirst().quantityDelta()).isEqualTo(-2);
    }
}
