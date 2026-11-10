package com.stockpilot.ordering.domain;

import com.stockpilot.catalog.domain.Product;
import com.stockpilot.inventory.domain.Warehouse;
import com.stockpilot.ordering.exception.InvalidOrderStateException;
import com.stockpilot.shared.domain.AuditableEntity;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 35)
    private String orderNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private OrderStatus status = OrderStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "total_net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalNetAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 500)
    private String notes;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderLine> lines = new ArrayList<>();

    protected PurchaseOrder() {
        // JPA standard default constructor
    }

    public PurchaseOrder(String orderNumber, UUID idempotencyKey, Warehouse warehouse, String notes) {
        this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.warehouse = Objects.requireNonNull(warehouse, "warehouse must not be null");
        this.notes = notes;
        this.status = OrderStatus.DRAFT;
        this.totalNetAmount = BigDecimal.ZERO;
    }

    public void addLine(Product product, int quantity, BigDecimal unitPrice) {
        if (this.status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(this.orderNumber, this.status.name(), "addLine");
        }
        OrderLine line = new OrderLine(this, product, quantity, unitPrice);
        this.lines.add(line);
        recalculateTotal();
    }

    public void confirm() {
        if (this.status != OrderStatus.DRAFT) {
            throw new InvalidOrderStateException(this.orderNumber, this.status.name(), "confirm");
        }
        if (this.lines.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an order with zero lines");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (this.status != OrderStatus.DRAFT && this.status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(this.orderNumber, this.status.name(), "cancel");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public void fulfill() {
        if (this.status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderStateException(this.orderNumber, this.status.name(), "fulfill");
        }
        this.status = OrderStatus.FULFILLED;
    }

    public void recalculateTotal() {
        this.totalNetAmount = this.lines.stream()
                .map(OrderLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = Objects.requireNonNull(orderNumber);
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(UUID idempotencyKey) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = Objects.requireNonNull(status);
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(Warehouse warehouse) {
        this.warehouse = Objects.requireNonNull(warehouse);
    }

    public BigDecimal getTotalNetAmount() {
        return totalNetAmount;
    }

    public void setTotalNetAmount(BigDecimal totalNetAmount) {
        this.totalNetAmount = Objects.requireNonNull(totalNetAmount);
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PurchaseOrder that = (PurchaseOrder) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
