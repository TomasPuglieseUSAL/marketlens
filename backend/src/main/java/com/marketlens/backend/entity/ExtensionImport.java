package com.marketlens.backend.entity;

import java.time.Instant;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "extension_imports")
public class ExtensionImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String marketplace;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "price_raw", length = 100)
    private String priceRaw;

    @Column(length = 10)
    private String currency;

    @Column(name = "seller_name", length = 255)
    private String sellerName;

    @Column(name = "external_id", length = 120)
    private String externalId;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "imported_by", nullable = false, length = 255)
    private String importedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_publication_id")
    private Publication processedPublication;

    @Column(name = "processed_at")
    private Instant processedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marketplace_reference_id")
    private MarketplaceReference marketplaceReference;

    @Column(name = "seller_profile_url", length = 2048)
    private String sellerProfileUrl;

    @Column(name = "seller_badge", length = 80)
    private String sellerBadge;

    @Column(name = "seller_sales_text", length = 120)
    private String sellerSalesText;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "seller_reputation_level")
    private Integer sellerReputationLevel;

    @Column(name = "external_seller_id")
    private Long externalSellerId;

    @Column(name = "facebook_join_year")
    private Integer facebookJoinYear;

    protected ExtensionImport() {
    }

    public ExtensionImport(
            String marketplace,
            String title,
            String priceRaw,
            String currency,
            String sellerName,
            String externalId,
            String description,
            String originalUrl,
            String importedBy) {

        this.marketplace = marketplace;
        this.title = title;
        this.priceRaw = priceRaw;
        this.currency = currency;
        this.sellerName = sellerName;
        this.externalId = externalId;
        this.description = description;
        this.originalUrl = originalUrl;
        this.importedBy = importedBy;
        this.createdAt = Instant.now();
    }

    public void setSellerMetadata(
            String sellerProfileUrl,
            String sellerBadge,
            String sellerSalesText) {

        this.sellerProfileUrl = sellerProfileUrl;
        this.sellerBadge = sellerBadge;
        this.sellerSalesText = sellerSalesText;
    }

    public Long getId() {
        return id;
    }

    public String getMarketplace() {
        return marketplace;
    }

    public String getTitle() {
        return title;
    }

    public String getSellerName() {
        return sellerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPriceRaw() {
        return priceRaw;
    }

    public String getCurrency() {
        return currency;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getDescription() {
        return description;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public Publication getProcessedPublication() {
        return processedPublication;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void markProcessed(Publication publication) {
        this.processedPublication = publication;
        this.processedAt = Instant.now();
    }

    public MarketplaceReference getMarketplaceReference() {
        return marketplaceReference;
    }

    public void setMarketplaceReference(
            MarketplaceReference marketplaceReference) {

        this.marketplaceReference = marketplaceReference;
    }

    public String getSellerProfileUrl() {
        return sellerProfileUrl;
    }

    public String getSellerBadge() {
        return sellerBadge;
    }

    public String getSellerSalesText() {
        return sellerSalesText;
    }

    public Integer getSellerReputationLevel() {
        return sellerReputationLevel;
    }

    public void setSellerReputationLevel(
            Integer sellerReputationLevel) {

        this.sellerReputationLevel = sellerReputationLevel;
    }

    public Long getExternalSellerId() {
        return externalSellerId;
    }

    public void setExternalSellerId(Long externalSellerId) {
        this.externalSellerId = externalSellerId;
    }

    public Integer getFacebookJoinYear() {
        return facebookJoinYear;
    }

    public void setFacebookJoinYear(Integer facebookJoinYear) {
        this.facebookJoinYear = facebookJoinYear;
    }
}