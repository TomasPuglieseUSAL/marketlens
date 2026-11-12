package com.marketlens.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceReferenceResponse(
        Long publicationId,
        BigDecimal publicationPriceArs,
        List<Long> referenceIds,
        int referenceCount,
        BigDecimal referencePriceArs,
        BigDecimal medianPriceArs,
        BigDecimal differenceArs,
        BigDecimal differencePercent,
        String analysisStatus,
        String warning
) {
}