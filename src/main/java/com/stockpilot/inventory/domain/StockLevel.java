package com.stockpilot.inventory.domain;

import com.stockpilot.catalog.domain.Product;
import com.stockpilot.shared.domain.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.util.Objects;

@Entity
@Table(
        name = "stock_level",
        uniqueConstraints = @UniqueConstraint(name = "uq_stock_level_product_warehouse", columnNames = {"product_id", "warehouse_id"})
)
public class StockLevel extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "total_qty", nullable = false)
    private Integer totalQty = 0;

    @Column(name = "reserved_qty", nullable = false)
    private Integer reservedQty = 0;

    /**
     * Computed directly by PostgreSQL: GENERATED ALWAYS AS (total_qty - reserved_qty) STORED.
     * Mapped as read-only in JPA so Hibernate never attempts to insert or update this column.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "available_qty", insertable = false, updatable = false)
    private Integer availableQty;

    protected StockLevel() {
        // JPA standard default constructor
    }

    public StockLevel(Product product, Warehouse warehouse, Integer totalQty) {
        this.product = Objects.requireNonNull(product, "Product must not be null");
        this.warehouse = Objects.requireNonNull(warehouse, "Warehouse must not be null");
        this.totalQty = Math.max(0, totalQty != null ? totalQty : 0);
        this.reservedQty = 0;
        this.availableQty = this.totalQty;
    }

    public void adjustTotalQty(int delta) {
        int newTotal = this.totalQty + delta;
        if (newTotal < this.reservedQty) {
            throw new IllegalArgumentException(String.format(
                    "Cannot adjust stock: new total (%d) would be lower than currently reserved stock (%d).",
                    newTotal, this.reservedQty));
        }
        this.totalQty = newTotal;
        this.availableQty = this.totalQty - this.reservedQty;
    }

    public void increaseReservation(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be strictly positive");
        }
        if (getAvailableQty() < quantity) {
            throw new IllegalStateException(String.format(
                    "Insufficient available stock: requested %d, available %d", quantity, getAvailableQty()));
        }
        this.reservedQty += quantity;
        this.availableQty = this.totalQty - this.reservedQty;
    }

    public void releaseReservation(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be strictly positive");
        }
        if (this.reservedQty < quantity) {
            throw new IllegalStateException(String.format(
                    "Cannot release %d units: only %d are currently reserved", quantity, this.reservedQty));
        }
        this.reservedQty -= quantity;
        this.availableQty = this.totalQty - this.reservedQty;
    }

    public void fulfillReservation(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Fulfillment quantity must be strictly positive");
        }
        if (this.reservedQty < quantity || this.totalQty < quantity) {
            throw new IllegalStateException("Cannot fulfill quantity exceeding reserved or total stock");
        }
        this.reservedQty -= quantity;
        this.totalQty -= quantity;
        this.availableQty = this.totalQty - this.reservedQty;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = Objects.requireNonNull(product);
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = Objects.requireNonNull(warehouse);
    }

    public Integer getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(Integer totalQty) {
        this.totalQty = totalQty;
    }

    public Integer getReservedQty() {
        return reservedQty;
    }

    public void setReservedQty(Integer reservedQty) {
        this.reservedQty = reservedQty;
    }

    public Integer getAvailableQty() {
        return availableQty != null ? availableQty : (totalQty - reservedQty);
    }
}
