package com.marketlens.backend.dto;

public record ExtensionPreviewRequest(
    String marketplace,
    String title,
    String price,
    String currency,
    String seller,
    String externalId,
    String description,
    String url,

    String sellerProfileUrl,
    String sellerBadge,
    java.math.BigDecimal temuSellerRating,
    Integer temuSellerReviewCount,
    java.math.BigDecimal temuProductRating,
    Integer temuProductReviewCount,
    String temuStoreId,
    Boolean temuSellerSectionObserved,
    String sellerSalesText,
    Integer sellerReputationLevel,
    String sellerId,
    Integer facebookJoinYear,
    String temuStoreTenureText,
    String temuProductSoldText,
    String temuStoreSoldText,
    java.math.BigDecimal mlProductRating,
    Integer mlProductReviewCount,
    String selectedOfferId,
    Boolean mlProductRatingUnavailable
) {
    // Contrato original que incluía selectedOfferId; conserva los tests Java.
    public ExtensionPreviewRequest(
            String marketplace, String title, String price, String currency,
            String seller, String externalId, String description, String url,
            String sellerProfileUrl, String sellerBadge,
            java.math.BigDecimal temuSellerRating, Integer temuSellerReviewCount,
            java.math.BigDecimal temuProductRating, Integer temuProductReviewCount,
            String temuStoreId, Boolean temuSellerSectionObserved,
            String sellerSalesText, Integer sellerReputationLevel, String sellerId,
            Integer facebookJoinYear, String temuStoreTenureText,
            String temuProductSoldText, String temuStoreSoldText,
            java.math.BigDecimal mlProductRating, Integer mlProductReviewCount,
            String selectedOfferId) {
        this(marketplace, title, price, currency, seller, externalId, description, url,
                sellerProfileUrl, sellerBadge, temuSellerRating, temuSellerReviewCount,
                temuProductRating, temuProductReviewCount, temuStoreId,
                temuSellerSectionObserved, sellerSalesText, sellerReputationLevel, sellerId,
                facebookJoinYear, temuStoreTenureText, temuProductSoldText,
                temuStoreSoldText, mlProductRating, mlProductReviewCount,
                selectedOfferId, null);
    }

    // Compatibilidad con el DTO anterior: clientes sin selección de
    // catálogo no deben proporcionar este campo.
    public ExtensionPreviewRequest(
            String marketplace, String title, String price, String currency,
            String seller, String externalId, String description, String url,
            String sellerProfileUrl, String sellerBadge,
            java.math.BigDecimal temuSellerRating, Integer temuSellerReviewCount,
            java.math.BigDecimal temuProductRating, Integer temuProductReviewCount,
            String temuStoreId, Boolean temuSellerSectionObserved,
            String sellerSalesText, Integer sellerReputationLevel, String sellerId,
            Integer facebookJoinYear, String temuStoreTenureText,
            String temuProductSoldText, String temuStoreSoldText,
            java.math.BigDecimal mlProductRating, Integer mlProductReviewCount) {
        this(marketplace, title, price, currency, seller, externalId, description, url,
                sellerProfileUrl, sellerBadge, temuSellerRating, temuSellerReviewCount,
                temuProductRating, temuProductReviewCount, temuStoreId,
                temuSellerSectionObserved, sellerSalesText, sellerReputationLevel, sellerId,
                facebookJoinYear, temuStoreTenureText, temuProductSoldText, temuStoreSoldText,
                mlProductRating, mlProductReviewCount, null);
    }

    // Constructor anterior para pruebas y clientes Java internos.
    public ExtensionPreviewRequest(
            String marketplace, String title, String price, String currency,
            String seller, String externalId, String description, String url,
            String sellerProfileUrl, String sellerBadge,
            java.math.BigDecimal temuSellerRating, Integer temuSellerReviewCount,
            java.math.BigDecimal temuProductRating, Integer temuProductReviewCount,
            String temuStoreId, Boolean temuSellerSectionObserved,
            String sellerSalesText, Integer sellerReputationLevel, String sellerId,
            Integer facebookJoinYear, String temuStoreTenureText,
            String temuProductSoldText, String temuStoreSoldText) {
        this(marketplace, title, price, currency, seller, externalId, description, url,
                sellerProfileUrl, sellerBadge, temuSellerRating, temuSellerReviewCount,
                temuProductRating, temuProductReviewCount, temuStoreId,
                temuSellerSectionObserved, sellerSalesText, sellerReputationLevel, sellerId,
                facebookJoinYear, temuStoreTenureText, temuProductSoldText,
                temuStoreSoldText, null, null, null);
    }
}