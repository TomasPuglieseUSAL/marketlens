package com.marketlens.backend.dto;

public record RiskCoverageFactorResponse(
    String code,
    String name,
    boolean available
) {
}