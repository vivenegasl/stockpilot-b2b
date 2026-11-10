package com.stockpilot.ordering.service;

import com.stockpilot.catalog.domain.Category;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.inventory.service.ReservationService;
import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.domain.PurchaseOrder;
import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderItemRequest;
import com.stockpilot.ordering.dto.OrderResponse;
import com.stockpilot.ordering.exception.InvalidOrderStateException;
import com.stockpilot.ordering.exception.OrderNotFoundException;
import com.stockpilot.ordering.repository.PurchaseOrderRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ReservationService reservationService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Warehouse warehouse;
    private Product product;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        Category category = new Category("PLCs", "Controladores");
        category.setId(1L);

        product = new Product("PLC-SIEM-S71500", "Siemens S7-1500", "CPU 1516-3 PN/DP", new BigDecimal("1250000.00"), category);
        product.setId(10L);

        warehouse = new Warehouse("WH-SCL-CENTRAL", "Centro Pudahuel", "Av. Industrial 1234");
        warehouse.setId(1L);

        idempotencyKey = UUID.randomUUID();
    }

    @Test
    @DisplayName("createDraftOrder creates order in DRAFT status with lines and calculated total")
    void createDraftOrder_Success() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> {
            PurchaseOrder po = i.getArgument(0);
            po.setId(101L);
            return po;
        });

        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 2)),
                "Orden de prueba para Minera"
        );

        OrderResponse response = orderService.createDraftOrder(request, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo(OrderStatus.DRAFT);
        assertThat(response.totalNetAmount()).isEqualByComparingTo(new BigDecimal("2500000.00"));
        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).sku()).isEqualTo("PLC-SIEM-S71500");
    }

    @Test
    @DisplayName("createDraftOrder throws IllegalArgumentException on duplicate product IDs")
    void createDraftOrder_DuplicateProducts_ThrowsException() {
        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(warehouse));

        CreateOrderRequest request = new CreateOrderRequest(
                1L,
                List.of(new OrderItemRequest(10L, 1), new OrderItemRequest(10L, 2)),
                "Duplicado"
        );

        assertThatThrownBy(() -> orderService.createDraftOrder(request, idempotencyKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate product ID");
    }

    @Test
    @DisplayName("confirmOrder transitions DRAFT order to CONFIRMED and delegates stock reservation")
    void confirmOrder_Success() {
        PurchaseOrder order = new PurchaseOrder("ORD-20261023-0001", idempotencyKey, warehouse, "Notas");
        order.setId(101L);
        order.addLine(product, 2, product.getUnitPrice());

        when(purchaseOrderRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.confirmOrder(101L);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(reservationService).reserveStock(eq(101L), eq("ORD-20261023-0001"), eq(1L), any());
    }

    @Test
    @DisplayName("confirmOrder fails with InvalidOrderStateException if order is not in DRAFT")
    void confirmOrder_NotDraft_ThrowsException() {
        PurchaseOrder order = new PurchaseOrder("ORD-001", idempotencyKey, warehouse, "Notas");
        order.setId(101L);
        order.addLine(product, 2, product.getUnitPrice());
        order.confirm(); // status is now CONFIRMED

        when(purchaseOrderRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirmOrder(101L))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    @DisplayName("cancelOrder on CONFIRMED order transitions to CANCELLED and releases reservations")
    void cancelOrder_Confirmed_Success() {
        PurchaseOrder order = new PurchaseOrder("ORD-001", idempotencyKey, warehouse, "Notas");
        order.setId(101L);
        order.addLine(product, 2, product.getUnitPrice());
        order.confirm();

        when(purchaseOrderRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.cancelOrder(101L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(reservationService).releaseStock(eq(101L), eq("ORD-001"));
    }

    @Test
    @DisplayName("fulfillOrder on CONFIRMED order transitions to FULFILLED and deducts stock")
    void fulfillOrder_Confirmed_Success() {
        PurchaseOrder order = new PurchaseOrder("ORD-001", idempotencyKey, warehouse, "Notas");
        order.setId(101L);
        order.addLine(product, 2, product.getUnitPrice());
        order.confirm();

        when(purchaseOrderRepository.findByIdWithDetails(101L)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse response = orderService.fulfillOrder(101L);

        assertThat(response.status()).isEqualTo(OrderStatus.FULFILLED);
        verify(reservationService).fulfillStock(eq(101L), eq("ORD-001"));
    }
}
