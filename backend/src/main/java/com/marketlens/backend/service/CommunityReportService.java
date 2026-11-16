package com.marketlens.backend.service;

import com.marketlens.backend.dto.CommunityReportReasonResponse;
import com.marketlens.backend.dto.CommunityReportSummaryResponse;
import com.marketlens.backend.entity.Report;
import com.marketlens.backend.entity.ReportReason;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CommunityReportService {
    public static final int REPORT_WINDOW_DAYS = 90;

    private final ReportRepository reportRepository;
    private final PublicationRepository publicationRepository;

    public CommunityReportService(
            ReportRepository reportRepository,
            PublicationRepository publicationRepository) {
        this.reportRepository = reportRepository;
        this.publicationRepository = publicationRepository;
    }

    @Transactional(readOnly = true)
    public CommunityReportSummaryResponse getSummary(Long publicationId) {
        if (!publicationRepository.existsById(publicationId)) {
            throw new ResourceNotFoundException("Publication not found: " + publicationId);
        }
        LocalDateTime now = LocalDateTime.now();
        List<Report> reports =
                reportRepository.findByPublicationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        publicationId, now.minusDays(REPORT_WINDOW_DAYS), now);
        return summarize(publicationId, reports, now);
    }

    /** Último reporte por usuario. Se evita sumar varias denuncias del mismo autor. */
    static CommunityReportSummaryResponse summarize(
            Long publicationId, List<Report> reports, LocalDateTime now) {
        LocalDateTime from = now.minusDays(REPORT_WINDOW_DAYS);
        Map<Long, Report> unique = new LinkedHashMap<>();
        int recentCount = 0;
        if (reports != null) {
            for (Report report : reports) {
                if (report == null || report.getCreatedAt() == null
                        || report.getCreatedAt().isBefore(from)
                        || report.getCreatedAt().isAfter(now) || report.getUser() == null
                        || report.getUser().getId() == null || report.getReason() == null) {
                    continue;
                }
                recentCount++;
                Long reporter = report.getUser().getId();
                Report previous = unique.get(reporter);
                if (previous == null || previous.getCreatedAt().isBefore(report.getCreatedAt())) {
                    unique.put(reporter, report);
                }
            }
        }

        Map<ReportReason, Integer> reasons = new EnumMap<>(ReportReason.class);
        for (Report report : unique.values()) {
            reasons.merge(report.getReason(), 1, Integer::sum);
        }
        List<CommunityReportReasonResponse> counts = reasons.entrySet().stream()
                .map(e -> new CommunityReportReasonResponse(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(CommunityReportReasonResponse::count).reversed()
                        .thenComparing(e -> e.reason().name()))
                .toList();

        return new CommunityReportSummaryResponse(
                publicationId, recentCount, unique.size(),
                CommunityReportRiskScoring.points(unique.size()), REPORT_WINDOW_DAYS, counts);
    }
}
