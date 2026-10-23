package com.stockpilot.inventory.service;

import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.service.LedgerService;
import com.stockpilot.catalog.domain.Category;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.exception.ProductNotFoundException;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.StockLevel;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.StockAdjustmentRequest;
import com.stockpilot.inventory.dto.StockLevelResponse;
import com.stockpilot.inventory.exception.InvalidStockAdjustmentException;
import com.stockpilot.inventory.exception.WarehouseNotFoundException;
import com.stockpilot.inventory.repository.StockLevelRepository;
import com.stockpilot.inventory.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private StockServiceImpl stockService;

    private Product testProduct;
    private Warehouse testWarehouse;
    private StockLevel testStockLevel;

    @BeforeEach
    void setUp() {
        Category category = new Category("Controladores", "PLCs");
        category.setId(1L);

        testProduct = new Product("PLC-SIEM-S71500", "Siemens S7-1500", null, new BigDecimal("1850000.00"), category);
        testProduct.setId(10L);

        testWarehouse = new Warehouse("WH-SCL-CENTRAL", "Centro Pudahuel", "Av. Vespucio 1500");
        testWarehouse.setId(20L);

        testStockLevel = new StockLevel(testProduct, testWarehouse, 10);
        testStockLevel.setId(30L);
    }

    @Test
    @DisplayName("US-003: Positive stock adjustment (INBOUND) increases totalQty and records ledger entry")
    void adjustStock_positiveInbound_success() {
        // Given
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                10L, 20L, 5, "Recepción lote importación", "OPERATOR-01"
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(warehouseRepository.findById(20L)).thenReturn(Optional.of(testWarehouse));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 20L))
                .thenReturn(Optional.of(testStockLevel));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StockLevelResponse response = stockService.adjustStock(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalQty()).isEqualTo(15);
        assertThat(response.availableQty()).isEqualTo(15);

        verify(stockLevelRepository).save(testStockLevel);
        verify(ledgerService).recordMovement(
                eq(10L), eq(20L), isNull(),
                eq(MovementType.INBOUND), eq(5),
                eq(15), eq(0),
                eq("Recepción lote importación"), eq("OPERATOR-01")
        );
    }

    @Test
    @DisplayName("US-003: Negative stock adjustment (ADJUSTMENT/Shrinkage) within available limit succeeds")
    void adjustStock_negativeAdjustment_success() {
        // Given: current total = 10, reserved = 0, delta = -3
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                10L, 20L, -3, "Ajuste por merma física", "SUPERVISOR-01"
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(warehouseRepository.findById(20L)).thenReturn(Optional.of(testWarehouse));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 20L))
                .thenReturn(Optional.of(testStockLevel));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StockLevelResponse response = stockService.adjustStock(request);

        // Then
        assertThat(response.totalQty()).isEqualTo(7);
        assertThat(response.availableQty()).isEqualTo(7);

        verify(ledgerService).recordMovement(
                eq(10L), eq(20L), isNull(),
                eq(MovementType.ADJUSTMENT), eq(-3),
                eq(7), eq(0),
                eq("Ajuste por merma física"), eq("SUPERVISOR-01")
        );
    }

    @Test
    @DisplayName("US-003: Rejects negative adjustment when delta would reduce total below reserved quantity")
    void adjustStock_negativeExceedsAvailable_throwsInvalidStockAdjustmentException() {
        // Given: total = 10, reserved = 6 (available = 4). Attempt to reduce by -5
        testStockLevel.increaseReservation(6);

        StockAdjustmentRequest request = new StockAdjustmentRequest(
                10L, 20L, -5, "Ajuste excesivo", "OPERATOR-01"
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(warehouseRepository.findById(20L)).thenReturn(Optional.of(testWarehouse));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 20L))
                .thenReturn(Optional.of(testStockLevel));

        // When & Then
        assertThatThrownBy(() -> stockService.adjustStock(request))
                .isInstanceOf(InvalidStockAdjustmentException.class);

        verify(stockLevelRepository, never()).save(any());
        verify(ledgerService, never()).recordMovement(any(), any(), any(), any(), any(Integer.class), any(Integer.class), any(Integer.class), any(), any());
    }

    @Test
    @DisplayName("US-003: Throws ProductNotFoundException when product does not exist")
    void adjustStock_productNotFound_throwsException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                999L, 20L, 5, "Test", "OPERATOR-01"
        );

        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.adjustStock(request))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    @DisplayName("US-003: Throws WarehouseNotFoundException when warehouse does not exist")
    void adjustStock_warehouseNotFound_throwsException() {
        StockAdjustmentRequest request = new StockAdjustmentRequest(
                10L, 999L, 5, "Test", "OPERATOR-01"
        );

        when(productRepository.findById(10L)).thenReturn(Optional.of(testProduct));
        when(warehouseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.adjustStock(request))
                .isInstanceOf(WarehouseNotFoundException.class);
    }

    @Test
    @DisplayName("US-004: Retrieves stock availability across warehouses for a product")
    void getStockByProduct_returnsStockLevels() {
        when(productRepository.existsById(10L)).thenReturn(true);
        when(stockLevelRepository.findByProductIdWithDetails(10L)).thenReturn(List.of(testStockLevel));

        List<StockLevelResponse> result = stockService.getStockByProduct(10L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().productSku()).isEqualTo("PLC-SIEM-S71500");
        assertThat(result.getFirst().warehouseCode()).isEqualTo("WH-SCL-CENTRAL");
        assertThat(result.getFirst().availableQty()).isEqualTo(10);
    }
}
