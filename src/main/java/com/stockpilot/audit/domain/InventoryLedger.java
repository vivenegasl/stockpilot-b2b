package com.stockpilot.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable append-only entity representing a historical inventory transaction or movement.
 * Protected against updates and deletions at both ORM and database layers (ADR-0006).
 */
@Entity
@Table(name = "inventory_ledger")
@Immutable
public class InventoryLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "order_id")
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, length = 30)
    private MovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private Integer quantityDelta;

    @Column(name = "resulting_total", nullable = false)
    private Integer resultingTotal;

    @Column(name = "resulting_reserved", nullable = false)
    private Integer resultingReserved;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "performed_by", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryLedger() {
        // JPA standard default constructor
    }

    public InventoryLedger(Long productId, Long warehouseId, Long orderId,
                           MovementType movementType, Integer quantityDelta,
                           Integer resultingTotal, Integer resultingReserved,
                           String reason, String performedBy) {
        this.productId = Objects.requireNonNull(productId, "Product ID must not be null");
        this.warehouseId = Objects.requireNonNull(warehouseId, "Warehouse ID must not be null");
        this.orderId = orderId;
        this.movementType = Objects.requireNonNull(movementType, "Movement type must not be null");
        this.quantityDelta = Objects.requireNonNull(quantityDelta, "Quantity delta must not be null");
        this.resultingTotal = Objects.requireNonNull(resultingTotal, "Resulting total must not be null");
        this.resultingReserved = Objects.requireNonNull(resultingReserved, "Resulting reserved must not be null");
        this.reason = Objects.requireNonNull(reason, "Reason must not be null").trim();
        this.performedBy = Objects.requireNonNull(performedBy, "Performed by must not be null").trim();
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public Integer getQuantityDelta() {
        return quantityDelta;
    }

    public Integer getResultingTotal() {
        return resultingTotal;
    }

    public Integer getResultingReserved() {
        return resultingReserved;
    }

    public String getReason() {
        return reason;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
