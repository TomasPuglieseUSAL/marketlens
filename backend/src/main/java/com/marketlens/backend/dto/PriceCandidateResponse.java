package com.marketlens.backend.dto;

import java.math.BigDecimal;

public record PriceCandidateResponse(
        Long publicationId,
        String title,
        String marketplace,
        BigDecimal price,
        double similarity,
        String verificationStatus
) {
}