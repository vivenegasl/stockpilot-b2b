package com.stockpilot.inventory.domain;

import com.stockpilot.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "stock_reservation")
public class StockReservation extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_level_id", nullable = false)
    private StockLevel stockLevel;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.ACTIVE;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected StockReservation() {
        // JPA standard default constructor
    }

    public StockReservation(StockLevel stockLevel, Long orderId, Integer quantity) {
        this.stockLevel = Objects.requireNonNull(stockLevel, "StockLevel must not be null");
        this.orderId = Objects.requireNonNull(orderId, "OrderId must not be null");
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be strictly positive");
        }
        this.quantity = quantity;
        this.status = ReservationStatus.ACTIVE;
    }

    public void release() {
        if (this.status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be released, current status: " + this.status);
        }
        this.status = ReservationStatus.RELEASED;
    }

    public void fulfill() {
        if (this.status != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE reservations can be fulfilled, current status: " + this.status);
        }
        this.status = ReservationStatus.FULFILLED;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public StockLevel getStockLevel() {
        return stockLevel;
    }

    public void setStockLevel(StockLevel stockLevel) {
        this.stockLevel = Objects.requireNonNull(stockLevel);
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = Objects.requireNonNull(orderId);
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockReservation that = (StockReservation) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
