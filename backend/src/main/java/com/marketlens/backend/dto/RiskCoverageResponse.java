package com.marketlens.backend.dto;

import java.util.List;

public record RiskCoverageResponse(
    Long importId,
    String referenceType,
    String status,
    int availableFactors,
    int totalFactors,
    List<RiskCoverageFactorResponse> factors,
    SellerPublicSignalsResponse sellerSignals
) {
}