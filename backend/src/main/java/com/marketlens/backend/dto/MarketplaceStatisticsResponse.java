package com.marketlens.backend.dto;

import com.marketlens.backend.entity.Marketplace;

public record MarketplaceStatisticsResponse(
    Marketplace marketplace,
    long totalPublications,
    long evaluatedPublications,
    double averageRiskScore,
    long lowRiskCount,
    long mediumRiskCount,
    long highRiskCount,
    long totalReports
) {
}