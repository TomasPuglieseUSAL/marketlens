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
    String temuStoreSoldText
) {
}