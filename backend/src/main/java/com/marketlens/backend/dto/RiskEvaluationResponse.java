package com.marketlens.backend.dto;

import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.RiskCoverageStatus;

import java.time.LocalDateTime;
import java.util.List;

public record RiskEvaluationResponse(
        Long id,
        Long publicationId,
        Integer score,
        RiskLevel level,
        LocalDateTime evaluatedAt,
        List<RiskFactorResponse> factors,
        RiskCoverageStatus coverageStatus,
        Integer availableFactors,
        Integer totalFactors) {
}