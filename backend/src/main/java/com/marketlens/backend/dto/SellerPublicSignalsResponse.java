package com.marketlens.backend.dto;

public record SellerPublicSignalsResponse(
    String sellerName,
    String sellerProfileUrl,
    String sellerBadge,
    String sellerSalesText,
    Integer sellerReputationLevel
) {
}