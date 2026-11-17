package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLibreSellerReputationRiskTest {
    private Publication publication(Integer observed, Integer verified, boolean fresh) {
        Publication publication = new Publication();
        publication.setMarketplace(Marketplace.MERCADO_LIBRE);
        publication.setTitle("SSD Kingston KC600");
        publication.setDescription("Nuevo, capacidad de 512 GB.");
        Seller seller = new Seller();
        seller.setName("TIENDA DE PRUEBA");
        seller.setMarketplace(Marketplace.MERCADO_LIBRE);
        seller.setReputationLevel(verified);
        publication.setSeller(seller);
        if (observed != null) {
            publication.setMlSellerReputationObserved(observed);
            publication.setMlSellerReputationObservedAt(
                    LocalDateTime.now().minusDays(fresh ? 1 : 31));
        }
        return publication;
    }

    @Test void observedLevelsRespectRiskGradient() {
        int[] levels = {1, 2, 3, 4, 5};
        int[] points = {25, 15, 8, 0, 0};
        for (int i = 0; i < levels.length; i++) {
            var result = new RiskEngine(new RiskCoverageCalculator())
                    .evaluate(publication(levels[i], null, true));
            assertEquals(points[i], result.getScore(), "Nivel " + levels[i]);
            assertEquals(2, result.getTotalFactors());
            assertEquals(2, result.getAvailableFactors());
            assertEquals(1, result.getFactors().size());
            assertEquals("ML_SELLER_REPUTATION_OBSERVED", result.getFactors().get(0).getCode());
        }
    }

    @Test void verifiedThreeAlsoContributesEightPoints() {
        var result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication(null, 3, true));
        assertEquals(8, result.getScore());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "MODERATE_SELLER_REPUTATION".equals(f.getCode())
                && f.getScoreContribution() == 8));
    }

    @Test void verifiedLevelTakesPrecedenceWithoutDoubleScoring() {
        var result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication(1, 3, true));
        assertEquals(8, result.getScore());
        assertTrue(result.getFactors().stream().noneMatch(f ->
                "ML_SELLER_REPUTATION_OBSERVED".equals(f.getCode())));
    }

    @Test void expiredObservationAddsNoPointsAndDoesNotCountCoverage() {
        var result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication(3, null, false));
        assertEquals(0, result.getScore());
        assertEquals(1, result.getAvailableFactors());
        assertTrue(result.getFactors().isEmpty());
    }
}
