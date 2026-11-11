package com.stockpilot.ordering.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter ordersCreatedCounter;
    private final Counter ordersConfirmedCounter;
    private final Counter ordersCancelledCounter;
    private final Counter ordersFulfilledCounter;
    private final Counter insufficientStockCounter;
    private final Counter idempotencyReplayCounter;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreatedCounter = Counter.builder("stockpilot.orders.created")
                .description("Total number of B2B purchase orders created in DRAFT")
                .register(registry);

        this.ordersConfirmedCounter = Counter.builder("stockpilot.orders.confirmed")
                .description("Total number of purchase orders confirmed with stock reserved")
                .register(registry);

        this.ordersCancelledCounter = Counter.builder("stockpilot.orders.cancelled")
                .description("Total number of purchase orders cancelled")
                .register(registry);

        this.ordersFulfilledCounter = Counter.builder("stockpilot.orders.fulfilled")
                .description("Total number of purchase orders fulfilled")
                .register(registry);

        this.insufficientStockCounter = Counter.builder("stockpilot.reservations.insufficient_stock")
                .description("Total number of reservation attempts that failed due to insufficient stock")
                .register(registry);

        this.idempotencyReplayCounter = Counter.builder("stockpilot.idempotency.replays")
                .description("Total number of idempotent HTTP replays served from cache")
                .register(registry);
    }

    public void incrementOrdersCreated() {
        ordersCreatedCounter.increment();
    }

    public void incrementOrdersConfirmed() {
        ordersConfirmedCounter.increment();
    }

    public void incrementOrdersCancelled() {
        ordersCancelledCounter.increment();
    }

    public void incrementOrdersFulfilled() {
        ordersFulfilledCounter.increment();
    }

    public void incrementInsufficientStock() {
        insufficientStockCounter.increment();
    }

    public void incrementIdempotencyReplay() {
        idempotencyReplayCounter.increment();
    }
}
