package com.marketlens.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public record AutomaticPriceAnalysisResponse(
        Long publicationId,
        BigDecimal publicationPriceArs,
        String analysisStatus,
        int referenceCount,

        BigDecimal referencePriceArs,
        BigDecimal medianPriceArs,
        BigDecimal differenceArs,
        BigDecimal differencePercent,

        Double pricePercentile,
        BigDecimal firstQuartileArs,
        BigDecimal thirdQuartileArs,
        BigDecimal interquartileRangeArs,

        Integer riskPoints,
        List<Long> referenceIds,
        List<AutomaticPriceCandidateResponse> candidates,
        String warning
) {
}