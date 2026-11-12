package com.marketlens.backend.dto;

import java.math.BigDecimal;

public record SellerListItemResponse(
    Long id,
    String name,
    BigDecimal reputation,
    Integer accountAgeDays
) {
}