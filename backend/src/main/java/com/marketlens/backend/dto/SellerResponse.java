package com.marketlens.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SellerResponse(
    Long id,
    String name,
    BigDecimal reputation,
    Integer accountAgeDays,
    LocalDateTime createdAt
) {
}