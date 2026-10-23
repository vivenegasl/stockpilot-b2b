package com.stockpilot.catalog.domain;

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
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "product")
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sku", nullable = false, unique = true, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "technical_spec", length = 2000)
    private String technicalSpec;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    protected Product() {
        // JPA standard default constructor
    }

    public Product(String sku, String name, String technicalSpec, BigDecimal unitPrice, Category category) {
        this.sku = Objects.requireNonNull(sku, "SKU must not be null").trim().toUpperCase();
        this.name = Objects.requireNonNull(name, "Product name must not be null").trim();
        this.technicalSpec = technicalSpec;
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price must not be null");
        this.category = Objects.requireNonNull(category, "Category must not be null");
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = Objects.requireNonNull(sku, "SKU must not be null").trim().toUpperCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "Product name must not be null").trim();
    }

    public String getTechnicalSpec() {
        return technicalSpec;
    }

    public void setTechnicalSpec(String technicalSpec) {
        this.technicalSpec = technicalSpec;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = Objects.requireNonNull(unitPrice, "Unit price must not be null");
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = Objects.requireNonNull(category, "Category must not be null");
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
