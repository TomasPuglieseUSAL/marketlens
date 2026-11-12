package com.marketlens.backend.dto;

public record RiskFactorResponse(
    String code,
    String description,
    Integer scoreContribution
) {
}