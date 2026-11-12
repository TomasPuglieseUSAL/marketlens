package com.marketlens.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sellers")
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String name;

    @Column(precision = 4, scale = 3)
    private BigDecimal reputation;

    @Column(name = "account_age_days")
    private Integer accountAgeDays;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "seller")
    private List<Publication> publications = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace", length = 30)
    private Marketplace marketplace;

    @Column(name = "external_seller_id")
    private Long externalSellerId;

    @Column(name = "reputation_level")
    private Integer reputationLevel;

    // Cantidad de ventas observadas públicamente; null = dato desconocido.
    @Column(name = "completed_sales")
    private Integer completedSales;

    // Momento en que MarketLens consultó esta cantidad a la fuente.
    @Column(name = "sales_observed_at")
    private LocalDateTime salesObservedAt;

    // Cantidad de productos publicados observada; no equivale a ventas.
    @Column(name = "listed_products_count")
    private Integer listedProductsCount;

    @Column(name = "products_observed_at")
    private LocalDateTime productsObservedAt;

    // Solo true cuando la fuente declara expresamente ventas insuficientes
    // para asignar una reputación. null = desconocido.
    @Column(name = "insufficient_sales_for_reputation")
    private Boolean insufficientSalesForReputation;

    @Column(name = "insufficient_sales_observed_at")
    private LocalDateTime insufficientSalesObservedAt;

    // Momento en que se inspeccionó correctamente la sección comercial del
    // vendedor y NO se mostró una cifra de ventas. No significa cero ventas.
    @Column(name = "sales_count_not_displayed_observed_at")
    private LocalDateTime salesCountNotDisplayedObservedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getReputation() {
        return reputation;
    }

    public void setReputation(BigDecimal reputation) {
        this.reputation = reputation;
    }

    public Integer getAccountAgeDays() {
        return accountAgeDays;
    }

    public void setAccountAgeDays(Integer accountAgeDays) {
        this.accountAgeDays = accountAgeDays;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<Publication> getPublications() {
        return publications;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public void setMarketplace(Marketplace marketplace) {
        this.marketplace = marketplace;
    }

    public Long getExternalSellerId() {
        return externalSellerId;
    }

    public void setExternalSellerId(Long externalSellerId) {
        this.externalSellerId = externalSellerId;
    }

    public Integer getReputationLevel() {
        return reputationLevel;
    }

    public void setReputationLevel(Integer reputationLevel) {
        this.reputationLevel = reputationLevel;
    }

    public Integer getCompletedSales() {
        return completedSales;
    }

    public void setCompletedSales(Integer completedSales) {
        if (completedSales != null && completedSales < 0) {
            throw new IllegalArgumentException("Las ventas no pueden ser negativas.");
        }
        this.completedSales = completedSales;
    }

    public LocalDateTime getSalesObservedAt() {
        return salesObservedAt;
    }

    public void setSalesObservedAt(LocalDateTime salesObservedAt) {
        this.salesObservedAt = salesObservedAt;
    }

    public Integer getListedProductsCount() {
        return listedProductsCount;
    }

    public void setListedProductsCount(Integer listedProductsCount) {
        if (listedProductsCount != null && listedProductsCount < 0) {
            throw new IllegalArgumentException("Los productos publicados no pueden ser negativos.");
        }
        this.listedProductsCount = listedProductsCount;
    }

    public LocalDateTime getProductsObservedAt() {
        return productsObservedAt;
    }

    public void setProductsObservedAt(LocalDateTime productsObservedAt) {
        this.productsObservedAt = productsObservedAt;
    }

    public Boolean getInsufficientSalesForReputation() {
        return insufficientSalesForReputation;
    }

    public void setInsufficientSalesForReputation(Boolean insufficientSalesForReputation) {
        this.insufficientSalesForReputation = insufficientSalesForReputation;
    }

    public LocalDateTime getInsufficientSalesObservedAt() {
        return insufficientSalesObservedAt;
    }

    public void setInsufficientSalesObservedAt(LocalDateTime insufficientSalesObservedAt) {
        this.insufficientSalesObservedAt = insufficientSalesObservedAt;
    }

    public LocalDateTime getSalesCountNotDisplayedObservedAt() {
        return salesCountNotDisplayedObservedAt;
    }

    public void setSalesCountNotDisplayedObservedAt(LocalDateTime observedAt) {
        this.salesCountNotDisplayedObservedAt = observedAt;
    }

    public Seller() {
    }
}