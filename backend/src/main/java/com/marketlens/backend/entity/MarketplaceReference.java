package com.marketlens.backend.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "marketplace_references")
public class MarketplaceReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Marketplace marketplace;

    @Column(name = "reference_type", nullable = false, length = 40)
    private String referenceType;

    @Column(name = "external_id", nullable = false, length = 120)
    private String externalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MarketplaceReference() {
    }

    public MarketplaceReference(
            Marketplace marketplace,
            String referenceType,
            String externalId) {

        this.marketplace = marketplace;
        this.referenceType = referenceType;
        this.externalId = externalId;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getExternalId() {
        return externalId;
    }
}