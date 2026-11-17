package com.marketlens.backend.service;

import com.marketlens.backend.dto.CommunityReportSummaryResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommunityReportEvaluationIntegrationTest {

    @Test void newEvaluationIncludesCurrentCommunityEvidenceWithoutChangingCoverage() {
        Publication publication = new Publication();
        publication.setMarketplace(Marketplace.MERCADO_LIBRE);
        publication.setDescription("Descripción observada del producto.");

        PublicationRepository publications = mock(PublicationRepository.class);
        RiskEvaluationRepository evaluations = mock(RiskEvaluationRepository.class);
        AutomaticPriceAnalysisService prices = mock(AutomaticPriceAnalysisService.class);
        CommunityReportService community = mock(CommunityReportService.class);

        when(publications.findById(5L)).thenReturn(Optional.of(publication));
        when(community.getSummary(5L)).thenReturn(
                new CommunityReportSummaryResponse(5L, 3, 2, 3, 90, List.of()));
        when(evaluations.save(any(RiskEvaluation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RiskEvaluationService service = new RiskEvaluationService(
                publications, evaluations,
                new RiskEngine(new RiskCoverageCalculator()), prices, community);

        var response = service.evaluate(5L);
        assertEquals(3, response.score());
        assertTrue(response.factors().stream().anyMatch(factor ->
                "COMMUNITY_REPORTS".equals(factor.code())
                        && factor.scoreContribution() == 3));
        assertEquals(2, response.totalFactors());
        assertEquals(1, response.availableFactors());
        verify(community).getSummary(5L);
        verify(evaluations).save(any(RiskEvaluation.class));
    }
}
