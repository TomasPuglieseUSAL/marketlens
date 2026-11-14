package com.marketlens.backend.dto;

import com.marketlens.backend.entity.Marketplace;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PublicationResponse(
    Long id,
    String title,
    String description,
    BigDecimal price,
    Long sellerId,
    String sellerName,
    String sellerBadge,
    java.math.BigDecimal temuSellerRating,
    Integer temuSellerReviewCount,
    BigDecimal temuProductRating,
    Integer temuProductReviewCount,
    java.time.LocalDateTime temuProductObservedAt,
    String temuStoreId,
    java.time.LocalDateTime temuSellerObservedAt,
    Marketplace marketplace,
    String externalId,
    String originalUrl,
    LocalDateTime createdAt
) {
}