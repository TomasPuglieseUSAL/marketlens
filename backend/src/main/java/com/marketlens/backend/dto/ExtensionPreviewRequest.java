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
    String sellerSalesText,
    Integer sellerReputationLevel,
    String sellerId,
    Integer facebookJoinYear
) {
}