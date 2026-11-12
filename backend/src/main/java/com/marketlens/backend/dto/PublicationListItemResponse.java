package com.marketlens.backend.dto;

import java.math.BigDecimal;

import com.marketlens.backend.entity.Marketplace;

public record PublicationListItemResponse(
    Long id,
    String title,
    BigDecimal price,
    Marketplace marketplace,
    Long sellerId,
    String sellerName
) {
}