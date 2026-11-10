package com.stockpilot.ordering.domain;

import com.stockpilot.catalog.domain.Product;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "order_line",
        uniqueConstraints = @UniqueConstraint(name = "uq_order_line_product", columnNames = {"order_id", "product_id"})
)
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * Computed by PostgreSQL: GENERATED ALWAYS AS (quantity * unit_price) STORED.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "line_total", insertable = false, updatable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected OrderLine() {
        // JPA standard default constructor
    }

    public OrderLine(PurchaseOrder order, Product product, Integer quantity, BigDecimal unitPrice) {
        this.order = Objects.requireNonNull(order, "PurchaseOrder must not be null");
        this.product = Objects.requireNonNull(product, "Product must not be null");
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be strictly positive");
        }
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("UnitPrice must be strictly positive");
        }
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public PurchaseOrder getOrder() {
        return order;
    }

    public void setOrder(PurchaseOrder order) {
        this.order = Objects.requireNonNull(order);
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = Objects.requireNonNull(product);
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal != null ? lineTotal : unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLine orderLine = (OrderLine) o;
        return Objects.equals(id, orderLine.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
