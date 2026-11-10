package com.stockpilot.ordering;

import com.stockpilot.TestcontainersConfiguration;
import com.stockpilot.catalog.domain.Product;
import com.stockpilot.catalog.repository.ProductRepository;
import com.stockpilot.inventory.domain.StockLevel;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.inventory.repository.StockLevelRepository;
import com.stockpilot.inventory.repository.WarehouseRepository;
import com.stockpilot.ordering.domain.OrderStatus;
import com.stockpilot.ordering.domain.PurchaseOrder;
import com.stockpilot.ordering.dto.CreateOrderRequest;
import com.stockpilot.ordering.dto.OrderItemRequest;
import com.stockpilot.ordering.dto.OrderResponse;
import com.stockpilot.ordering.repository.PurchaseOrderRepository;
import com.stockpilot.ordering.service.OrderService;
import com.stockpilot.shared.exception.InsufficientStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.DockerClientFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@EnabledIf("isDockerAvailable")
class OrderReservationConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(OrderReservationConcurrencyTest.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private StockLevelRepository stockLevelRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Test
    @DisplayName("50 concurrent threads competing for 1 available SKU unit: exactly 1 succeeds, 49 fail with InsufficientStockException, zero oversell")
    void fiftyConcurrentThreads_ZeroOversell_Guaranteed() throws InterruptedException {
        // 1. Arrange: Setup product with exactly 1 unit available in WH-SCL-CENTRAL
        Warehouse warehouse = warehouseRepository.findAll().stream()
                .filter(w -> "WH-SCL-CENTRAL".equals(w.getCode()))
                .findFirst()
                .orElseThrow();

        Product product = productRepository.findAll().stream()
                .filter(p -> "PLC-SIEM-S71500".equals(p.getSku()))
                .findFirst()
                .orElseThrow();

        StockLevel stockLevel = stockLevelRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> new StockLevel(product, warehouse, 1));

        stockLevel.setTotalQty(1);
        stockLevel.setReservedQty(0);
        stockLevelRepository.saveAndFlush(stockLevel);

        // 2. Pre-create 50 distinct orders in DRAFT status, each requesting 1 unit
        int numberOfThreads = 50;
        List<Long> orderIds = new ArrayList<>();

        for (int i = 0; i < numberOfThreads; i++) {
            UUID idempotencyKey = UUID.randomUUID();
            CreateOrderRequest request = new CreateOrderRequest(
                    warehouse.getId(),
                    List.of(new OrderItemRequest(product.getId(), 1)),
                    "Stress test order #" + i
            );
            OrderResponse draftOrder = orderService.createDraftOrder(request, idempotencyKey);
            orderIds.add(draftOrder.id());
        }

        assertThat(orderIds).hasSize(numberOfThreads);

        // 3. Multithreading execution harness: 50 concurrent threads coordinated by CountDownLatch
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger conflictCounter = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCounter = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final Long orderId = orderIds.get(i);
            executor.submit(() -> {
                try {
                    startGun.await(); // wait for start gun so all 50 threads fire at the exact same nanosecond
                    orderService.confirmOrder(orderId);
                    successCounter.incrementAndGet();
                    log.info("Thread acquired the single stock unit for order ID: {}", orderId);
                } catch (InsufficientStockException ex) {
                    conflictCounter.incrementAndGet();
                } catch (Throwable t) {
                    log.error("Unexpected error in thread for order ID {}: {}", orderId, t.getMessage(), t);
                    unexpectedErrorCounter.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 4. Act: Fire the starting gun
        long startTime = System.currentTimeMillis();
        startGun.countDown();

        boolean completedInTime = finishLatch.await(30, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;
        executor.shutdown();

        log.info("Concurrency stress test finished in {} ms. Success: {}, Conflicts: {}, Unexpected: {}",
                duration, successCounter.get(), conflictCounter.get(), unexpectedErrorCounter.get());

        // 5. Assert: Inviolable mathematical invariants
        assertThat(completedInTime).isTrue();
        assertThat(unexpectedErrorCounter.get()).isEqualTo(0);
        assertThat(successCounter.get()).isEqualTo(1);
        assertThat(conflictCounter.get()).isEqualTo(49);

        // Verify database state directly
        StockLevel freshStock = stockLevelRepository
                .findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseThrow();

        assertThat(freshStock.getTotalQty()).isEqualTo(1);
        assertThat(freshStock.getReservedQty()).isEqualTo(1);
        assertThat(freshStock.getAvailableQty()).isEqualTo(0);

        // Exactly 1 order is CONFIRMED, 49 remain in DRAFT
        long confirmedCount = orderIds.stream()
                .map(id -> purchaseOrderRepository.findById(id).orElseThrow())
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED)
                .count();

        long draftCount = orderIds.stream()
                .map(id -> purchaseOrderRepository.findById(id).orElseThrow())
                .filter(order -> order.getStatus() == OrderStatus.DRAFT)
                .count();

        assertThat(confirmedCount).isEqualTo(1);
        assertThat(draftCount).isEqualTo(49);
    }
}
