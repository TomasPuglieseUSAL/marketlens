package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommunityReportRiskScoringTest {

    @Test void reportCountHasBoundedImpact() {
        assertNull(CommunityReportRiskScoring.evaluate(0));
        assertEquals(0, CommunityReportRiskScoring.evaluate(1).points());
        assertEquals(3, CommunityReportRiskScoring.evaluate(2).points());
        assertEquals(3, CommunityReportRiskScoring.evaluate(3).points());
        assertEquals(8, CommunityReportRiskScoring.evaluate(4).points());
        assertEquals(8, CommunityReportRiskScoring.evaluate(1000).points());
        assertTrue(CommunityReportRiskScoring.evaluate(2).description().contains("no verificados"));
    }

    @Test void engineRecordsCommunityFactorAsHistoricalSnapshot() {
        Publication publication = mock(Publication.class);
        when(publication.getMarketplace()).thenReturn(Marketplace.MERCADO_LIBRE);

        RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());
        var withReports = engine.evaluate(publication, null, null, 0, 4);
        var withoutReports = engine.evaluate(publication, null, null, 0, 0);

        assertEquals(withoutReports.getScore() + 8, withReports.getScore());
        assertTrue(withReports.getFactors().stream().anyMatch(factor ->
                "COMMUNITY_REPORTS".equals(factor.getCode())
                && factor.getScoreContribution() == 8));
        assertTrue(withoutReports.getFactors().stream().noneMatch(factor ->
                "COMMUNITY_REPORTS".equals(factor.getCode())));
        assertEquals(withoutReports.getAvailableFactors(), withReports.getAvailableFactors());
        assertEquals(withoutReports.getTotalFactors(), withReports.getTotalFactors());
    }

    @Test void aSingleCommunityReportIsInformative() {
        Publication publication = mock(Publication.class);
        when(publication.getMarketplace()).thenReturn(Marketplace.MERCADO_LIBRE);
        var result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication, null, null, 0, 1);
        assertTrue(result.getFactors().stream().anyMatch(factor ->
                "COMMUNITY_REPORTS".equals(factor.getCode())
                && factor.getScoreContribution() == 0));
    }
}
