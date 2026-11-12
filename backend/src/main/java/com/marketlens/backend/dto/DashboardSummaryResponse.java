package com.marketlens.backend.dto;

import java.util.Map;

public record DashboardSummaryResponse(

    long totalPublications,

    long totalSellers,

    long totalEvaluations,

    long totalReports,

    double averageRiskScore,

    Map<String, Long> evaluationsByRiskLevel,

    Map<String, Long> publicationsByMarketplace

) {
}