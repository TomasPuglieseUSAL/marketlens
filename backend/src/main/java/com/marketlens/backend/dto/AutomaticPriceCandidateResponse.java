package com.marketlens.backend.dto;

import java.math.BigDecimal;

public record AutomaticPriceCandidateResponse(
        Long publicationId,
        String title,
        BigDecimal priceArs,
        double similarity,
        String status,
        String reason
) {
}