package com.marketlens.backend.service;

import com.marketlens.backend.dto.CommunityReportSummaryResponse;
import com.marketlens.backend.entity.Report;
import com.marketlens.backend.entity.ReportReason;
import com.marketlens.backend.entity.User;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.ReportRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CommunityReportServiceTest {

    private static Report report(long userId, ReportReason reason, LocalDateTime at) {
        Report report = mock(Report.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(report.getUser()).thenReturn(user);
        when(report.getReason()).thenReturn(reason);
        when(report.getCreatedAt()).thenReturn(at);
        return report;
    }

    @Test void repeatedReportsFromSameUserCountOnceAndLatestReasonWins() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 12, 0);
        CommunityReportSummaryResponse summary = CommunityReportService.summarize(
                20L, List.of(
                    report(1L, ReportReason.PAYMENT_OUTSIDE_PLATFORM, now.minusDays(1)),
                    report(1L, ReportReason.OTHER, now.minusDays(3)),
                    report(2L, ReportReason.SUSPICIOUS_SELLER, now.minusDays(2))
                ), now);
        assertEquals(3, summary.recentReportCount());
        assertEquals(2, summary.uniqueReporterCount());
        assertEquals(3, summary.riskPoints());
        assertEquals(90, summary.windowDays());
        assertEquals(2, summary.reasons().size());
        assertTrue(summary.reasons().stream().noneMatch(r -> r.reason() == ReportReason.OTHER));
        assertTrue(summary.reasons().stream().anyMatch(r ->
                r.reason() == ReportReason.PAYMENT_OUTSIDE_PLATFORM && r.count() == 1));
    }

    @Test void fourIndependentReportsCapContributionAndAggregateReasons() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 12, 0);
        CommunityReportSummaryResponse summary = CommunityReportService.summarize(
                2L, List.of(
                        report(1L, ReportReason.SUSPICIOUS_PRICE, now.minusDays(1)),
                        report(2L, ReportReason.SUSPICIOUS_PRICE, now.minusDays(1)),
                        report(3L, ReportReason.SUSPICIOUS_PRICE, now.minusDays(2)),
                        report(4L, ReportReason.OTHER, now.minusDays(1)),
                        report(4L, ReportReason.OTHER, now.minusDays(2))
                ), now);
        assertEquals(5, summary.recentReportCount());
        assertEquals(4, summary.uniqueReporterCount());
        assertEquals(8, summary.riskPoints());
        assertEquals(ReportReason.SUSPICIOUS_PRICE, summary.reasons().get(0).reason());
        assertEquals(3, summary.reasons().get(0).count());
    }

    @Test void excludeExpiredAndFutureEvidence() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 12, 0);
        CommunityReportSummaryResponse summary = CommunityReportService.summarize(
                2L, List.of(
                        report(1L, ReportReason.OTHER, now.minusDays(91)),
                        report(2L, ReportReason.OTHER, now.plusMinutes(1)),
                        report(3L, ReportReason.OTHER, now.minusDays(1))
                ), now);
        assertEquals(1, summary.recentReportCount());
        assertEquals(1, summary.uniqueReporterCount());
        assertEquals(0, summary.riskPoints());
    }

    @Test void endpointServiceLimitsQueryByTimeAndChecksPublication() {
        ReportRepository reports = mock(ReportRepository.class);
        PublicationRepository publications = mock(PublicationRepository.class);
        when(publications.existsById(21L)).thenReturn(true);
        when(reports.findByPublicationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(21L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(List.of());

        CommunityReportSummaryResponse result =
                new CommunityReportService(reports, publications).getSummary(21L);

        assertEquals(21L, result.publicationId());
        assertEquals(0, result.uniqueReporterCount());
        verify(reports).findByPublicationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                eq(21L), any(LocalDateTime.class), any(LocalDateTime.class));
    }
}
