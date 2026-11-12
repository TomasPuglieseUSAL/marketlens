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
    Marketplace marketplace,
    String externalId,
    String originalUrl,
    LocalDateTime createdAt
) {
}