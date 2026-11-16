package com.marketlens.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "publications")
public class Publication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private Seller seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Marketplace marketplace;

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "original_url", length = 1000)
    private String originalUrl;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "facebook_join_year")
    private Integer facebookJoinYear;

    // Distintivo observado en el producto, no acreditación de MarketLens.
    @Column(name = "seller_badge", length = 80)
    private String sellerBadge;

    // Puntuación observada de una tienda Temu, escala 0–5.
    // Independiente de las valoraciones del producto y de Mercado Libre.
    @Column(name = "temu_seller_rating", precision = 2, scale = 1)
    private BigDecimal temuSellerRating;

    @Column(name = "temu_seller_review_count")
    private Integer temuSellerReviewCount;

    // Opiniones agregadas del PRODUCTO Mercado Libre (pueden ser compartidas
    // por ofertas de varios vendedores; no verifican su reputación).
    @Column(name = "ml_product_rating", precision = 2, scale = 1)
    private BigDecimal mlProductRating;

    @Column(name = "ml_product_review_count")
    private Integer mlProductReviewCount;

    // Solo TRUE señala que se inspeccionó la ficha principal y no mostró rating.
    @Column(name = "ml_product_rating_unavailable")
    private Boolean mlProductRatingUnavailable;

    @Column(name = "ml_product_observed_at")
    private LocalDateTime mlProductObservedAt;

    // Termómetro público de vendedor, SIN corroboración OAuth.
    @Column(name = "ml_seller_reputation_observed")
    private Integer mlSellerReputationObserved;

    @Column(name = "ml_seller_reputation_observed_at")
    private LocalDateTime mlSellerReputationObservedAt;

    @Column(name = "temu_product_rating", precision = 2, scale = 1)
    private BigDecimal temuProductRating;

    @Column(name = "temu_product_review_count")
    private Integer temuProductReviewCount;

    @Column(name = "temu_product_observed_at")
    private LocalDateTime temuProductObservedAt;

    @Column(name = "temu_store_tenure_text", length = 60)
    private String temuStoreTenureText;

    @Column(name = "temu_store_tenure_observed_at")
    private LocalDateTime temuStoreTenureObservedAt;

    @Column(name = "temu_product_sold_text", length = 60)
    private String temuProductSoldText;

    @Column(name = "temu_product_sales_observed_at")
    private LocalDateTime temuProductSalesObservedAt;

    @Column(name = "temu_store_sold_text", length = 60)
    private String temuStoreSoldText;

    @Column(name = "temu_store_sales_observed_at")
    private LocalDateTime temuStoreSalesObservedAt;

    @Column(name = "temu_store_id", length = 30)
    private String temuStoreId;

    @Column(name = "temu_seller_section_observed")
    private Boolean temuSellerSectionObserved;

    @Column(name = "temu_seller_observed_at")
    private LocalDateTime temuSellerObservedAt;

    @OneToMany(mappedBy = "publication", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RiskEvaluation> evaluations = new ArrayList<>();

    public Publication() {
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Seller getSeller() {
        return seller;
    }

    public void setSeller(Seller seller) {
        this.seller = seller;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public void setMarketplace(Marketplace marketplace) {
        this.marketplace = marketplace;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<RiskEvaluation> getEvaluations() {
        return evaluations;
    }

    public void setEvaluations(List<RiskEvaluation> evaluations) {
        this.evaluations = evaluations;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getSellerBadge() {
        return sellerBadge;
    }

    public BigDecimal getTemuSellerRating() {
        return temuSellerRating;
    }

    public void setTemuSellerRating(BigDecimal rating) {
        this.temuSellerRating = rating;
    }

    public Integer getTemuSellerReviewCount() { return temuSellerReviewCount; }
    public void setTemuSellerReviewCount(Integer count) { this.temuSellerReviewCount = count; }
    public BigDecimal getMlProductRating() { return mlProductRating; }
    public void setMlProductRating(BigDecimal value) { this.mlProductRating = value; }
    public Integer getMlProductReviewCount() { return mlProductReviewCount; }
    public Boolean getMlProductRatingUnavailable() { return mlProductRatingUnavailable; }
    public void setMlProductRatingUnavailable(Boolean unavailable) {
        this.mlProductRatingUnavailable = unavailable;
    }
    public void setMlProductReviewCount(Integer value) { this.mlProductReviewCount = value; }
    public LocalDateTime getMlProductObservedAt() { return mlProductObservedAt; }
    public void setMlProductObservedAt(LocalDateTime value) { this.mlProductObservedAt = value; }
    public Integer getMlSellerReputationObserved() { return mlSellerReputationObserved; }
    public void setMlSellerReputationObserved(Integer value) { this.mlSellerReputationObserved = value; }
    public LocalDateTime getMlSellerReputationObservedAt() { return mlSellerReputationObservedAt; }
    public void setMlSellerReputationObservedAt(LocalDateTime value) { this.mlSellerReputationObservedAt = value; }
    public BigDecimal getTemuProductRating() { return temuProductRating; }
    public void setTemuProductRating(BigDecimal rating) { this.temuProductRating = rating; }
    public Integer getTemuProductReviewCount() { return temuProductReviewCount; }
    public void setTemuProductReviewCount(Integer count) { this.temuProductReviewCount = count; }
    public LocalDateTime getTemuProductObservedAt() { return temuProductObservedAt; }
    public void setTemuProductObservedAt(LocalDateTime at) { this.temuProductObservedAt = at; }
    public String getTemuStoreTenureText() { return temuStoreTenureText; }
    public void setTemuStoreTenureText(String value) { this.temuStoreTenureText = value; }
    public LocalDateTime getTemuStoreTenureObservedAt() { return temuStoreTenureObservedAt; }
    public void setTemuStoreTenureObservedAt(LocalDateTime date) { this.temuStoreTenureObservedAt = date; }
    public String getTemuProductSoldText() { return temuProductSoldText; }
    public void setTemuProductSoldText(String value) { this.temuProductSoldText = value; }
    public LocalDateTime getTemuProductSalesObservedAt() { return temuProductSalesObservedAt; }
    public void setTemuProductSalesObservedAt(LocalDateTime date) { this.temuProductSalesObservedAt = date; }
    public String getTemuStoreSoldText() { return temuStoreSoldText; }
    public void setTemuStoreSoldText(String value) { this.temuStoreSoldText = value; }
    public LocalDateTime getTemuStoreSalesObservedAt() { return temuStoreSalesObservedAt; }
    public void setTemuStoreSalesObservedAt(LocalDateTime date) { this.temuStoreSalesObservedAt = date; }
    public String getTemuStoreId() { return temuStoreId; }
    public void setTemuStoreId(String id) { this.temuStoreId = id; }
    public Boolean getTemuSellerSectionObserved() { return temuSellerSectionObserved; }
    public void setTemuSellerSectionObserved(Boolean observed) { this.temuSellerSectionObserved = observed; }
    public LocalDateTime getTemuSellerObservedAt() { return temuSellerObservedAt; }
    public void setTemuSellerObservedAt(LocalDateTime observedAt) { this.temuSellerObservedAt = observedAt; }

    public void setSellerBadge(String sellerBadge) {
        this.sellerBadge = sellerBadge;
    }

    public Integer getFacebookJoinYear() {
        return facebookJoinYear;
    }

    public void setFacebookJoinYear(Integer facebookJoinYear) {
        this.facebookJoinYear = facebookJoinYear;
    }
}