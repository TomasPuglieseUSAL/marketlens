package com.marketlens.backend.dto;

import java.util.List;

/** Resumen agregado: no divulga identidades ni descripciones de autores. */
public record CommunityReportSummaryResponse(
        Long publicationId,
        int recentReportCount,
        int uniqueReporterCount,
        int riskPoints,
        int windowDays,
        List<CommunityReportReasonResponse> reasons) { }
