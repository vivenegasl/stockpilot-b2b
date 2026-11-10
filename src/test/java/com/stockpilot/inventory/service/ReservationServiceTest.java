package com.stockpilot.inventory.service;

import com.stockpilot.audit.domain.MovementType;
import com.stockpilot.audit.service.LedgerService;
import com.stockpilot.catalog.domain.Category;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.ReservationStatus;
import com.stockpilot.inventory.domain.StockLevel;
import com.stockpilot.inventory.domain.StockReservation;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.dto.ReservationItem;
import com.stockpilot.inventory.repository.StockLevelRepository;
import com.stockpilot.inventory.repository.StockReservationRepository;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.shared.exception.InsufficientStockException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private StockReservationRepository stockReservationRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private LedgerService ledgerService;

    @InjectMocks
    private ReservationServiceImpl reservationService;

    private Warehouse warehouse;
    private Product product1;
    private Product product2;
    private StockLevel stockLevel1;
    private StockLevel stockLevel2;

    @BeforeEach
    void setUp() {
        Category category = new Category("PLCs", "Controladores");
        category.setId(1L);

        product1 = new Product("PLC-SIEM-S71500", "Siemens S7-1500", "CPU 1516-3 PN/DP", new BigDecimal("1250000.00"), category);
        product1.setId(10L);

        product2 = new Product("VAR-ABB-ACS580", "Variador ABB", "Variador 5.5kW", new BigDecimal("850000.00"), category);
        product2.setId(20L);

        warehouse = new Warehouse("WH-SCL-CENTRAL", "Centro Pudahuel", "Av. Industrial 1234");
        warehouse.setId(1L);

        stockLevel1 = new StockLevel(product1, warehouse, 10);
        stockLevel1.setId(100L);

        stockLevel2 = new StockLevel(product2, warehouse, 5);
        stockLevel2.setId(200L);
    }

    @Test
    @DisplayName("reserveStock successfully reserves stock and records in ledger in productId ASC order")
    void reserveStock_Success() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(20L)).thenReturn(Optional.of(product2));

        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 1L))
                .thenReturn(Optional.of(stockLevel1));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(20L, 1L))
                .thenReturn(Optional.of(stockLevel2));

        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(i -> i.getArgument(0));
        when(stockReservationRepository.save(any(StockReservation.class))).thenAnswer(i -> {
            StockReservation r = i.getArgument(0);
            r.setId(999L);
            return r;
        });

        // Send items in reverse order [20, 10] to verify sort by productId ASC
        List<ReservationItem> items = List.of(
                new ReservationItem(20L, 2),
                new ReservationItem(10L, 3)
        );

        List<StockReservation> result = reservationService.reserveStock(500L, "ORD-20261023-0001", 1L, items);

        assertThat(result).hasSize(2);
        assertThat(stockLevel1.getReservedQty()).isEqualTo(3);
        assertThat(stockLevel1.getAvailableQty()).isEqualTo(7);
        assertThat(stockLevel2.getReservedQty()).isEqualTo(2);
        assertThat(stockLevel2.getAvailableQty()).isEqualTo(3);

        verify(ledgerService).recordMovement(eq(10L), eq(1L), eq(500L), eq(MovementType.RESERVATION),
                eq(-3), eq(10), eq(3), any(), eq("SYSTEM"));
        verify(ledgerService).recordMovement(eq(20L), eq(1L), eq(500L), eq(MovementType.RESERVATION),
                eq(-2), eq(5), eq(2), any(), eq("SYSTEM"));
    }

    @Test
    @DisplayName("reserveStock throws InsufficientStockException when requested > available")
    void reserveStock_InsufficientStock() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product1));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 1L))
                .thenReturn(Optional.of(stockLevel1)); // available is 10

        List<ReservationItem> items = List.of(new ReservationItem(10L, 15)); // requested 15

        assertThatThrownBy(() -> reservationService.reserveStock(500L, "ORD-001", 1L, items))
                .isInstanceOf(InsufficientStockException.class);
    }

    @Test
    @DisplayName("releaseStock restores reserved stock and marks reservation as RELEASED")
    void releaseStock_Success() {
        stockLevel1.increaseReservation(4); // reserved=4, available=6
        StockReservation reservation = new StockReservation(stockLevel1, 500L, 4);
        reservation.setId(101L);

        when(stockReservationRepository.findByOrderIdAndStatusWithDetails(500L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 1L))
                .thenReturn(Optional.of(stockLevel1));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(i -> i.getArgument(0));

        reservationService.releaseStock(500L, "ORD-001");

        assertThat(stockLevel1.getReservedQty()).isEqualTo(0);
        assertThat(stockLevel1.getAvailableQty()).isEqualTo(10);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        verify(ledgerService).recordMovement(eq(10L), eq(1L), eq(500L), eq(MovementType.RELEASE),
                eq(4), eq(10), eq(0), any(), eq("SYSTEM"));
    }

    @Test
    @DisplayName("fulfillStock deducts both total_qty and reserved_qty and marks reservation as FULFILLED")
    void fulfillStock_Success() {
        stockLevel1.increaseReservation(4); // total=10, reserved=4, available=6
        StockReservation reservation = new StockReservation(stockLevel1, 500L, 4);
        reservation.setId(101L);

        when(stockReservationRepository.findByOrderIdAndStatusWithDetails(500L, ReservationStatus.ACTIVE))
                .thenReturn(List.of(reservation));
        when(stockLevelRepository.findByProductAndWarehouseForUpdate(10L, 1L))
                .thenReturn(Optional.of(stockLevel1));
        when(stockLevelRepository.save(any(StockLevel.class))).thenAnswer(i -> i.getArgument(0));

        reservationService.fulfillStock(500L, "ORD-001");

        assertThat(stockLevel1.getTotalQty()).isEqualTo(6);
        assertThat(stockLevel1.getReservedQty()).isEqualTo(0);
        assertThat(stockLevel1.getAvailableQty()).isEqualTo(6);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.FULFILLED);

        verify(ledgerService).recordMovement(eq(10L), eq(1L), eq(500L), eq(MovementType.FULFILLMENT),
                eq(-4), eq(6), eq(0), any(), eq("SYSTEM"));
    }
}
