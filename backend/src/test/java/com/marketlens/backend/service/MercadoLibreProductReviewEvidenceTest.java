package com.marketlens.backend.service;

import com.marketlens.backend.entity.*;
import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLibreProductReviewEvidenceTest {

    private Publication listing() {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setTitle("SSD Kingston 1 TB");
        p.setDescription("SSD NVMe, nuevo, 1 TB");
        Seller s = new Seller();
        s.setName("Tienda de prueba");
        s.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setSeller(s);
        return p;
    }

    @Test void threeFactorsRequireProductReviewPairAndSellerEvidence() {
        Publication p = listing();
        p.setMlProductRating(new BigDecimal("4.7"));
        p.setMlProductReviewCount(286);
        p.setMlProductObservedAt(LocalDateTime.now().minusDays(1));
        p.setMlSellerReputationObserved(5);
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(1));

        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(3, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertTrue(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "ML_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode())
                && f.getScoreContribution() == 0));
        assertNull(p.getSeller().getReputationLevel(),
                "La reputación observada no debe simular una corroboración OAuth");
    }

    @Test void productReviewsDoNotReplaceMissingSellerReputationForLowRisk() {
        Publication p = listing();
        p.setMlProductRating(new BigDecimal("4.7"));
        p.setMlProductReviewCount(286);
        p.setMlProductObservedAt(LocalDateTime.now());
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(2, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
        assertFalse(RiskCoverageCalculator.factors(p).stream()
                .filter(f -> f.code().equals("SELLER_REPUTATION")).findFirst().orElseThrow().available());
    }

    @Test void missingOrStaleMetricsNeverCount() {
        Publication p = listing();
        p.setMlProductRating(new BigDecimal("4.7"));
        p.setMlProductReviewCount(null);
        p.setMlProductObservedAt(LocalDateTime.now());
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        p.setMlProductReviewCount(286);
        p.setMlProductObservedAt(LocalDateTime.now().minusDays(31));
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        p.setMlProductObservedAt(LocalDateTime.now());
        assertTrue(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        assertFalse(MercadoLibreProductReviewEvidence.hasMetrics(new BigDecimal("0.0"), 286));
        assertFalse(MercadoLibreProductReviewEvidence.hasMetrics(new BigDecimal("4.7"), 0));
    }

    @Test void observedThermometerExpiresAndDoesNotBecomeVerifiedLevel() {
        Publication p = listing();
        p.setMlSellerReputationObserved(4);
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(1));
        assertTrue(MercadoLibreSellerEvidence.hasObservedReputation(p, LocalDateTime.now()));
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(31));
        assertFalse(MercadoLibreSellerEvidence.hasObservedReputation(p, LocalDateTime.now()));
    }

    @Test void newImportTransfersObservationsWithoutErasingValidPriorMetrics() {
        Publication p = listing();
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston", "199999", "ARS", "Tienda de prueba",
                "MLAU3515247206", "SSD nuevo",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU3515247206", "demo");
        imported.setMlProductReviews(new BigDecimal("4.6"), 57);
        imported.setSellerReputationLevel(4);
        ExtensionProcessingService.applyMercadoLibreObservedEvidence(p, imported);
        assertEquals(57, p.getMlProductReviewCount());
        assertEquals(4, p.getMlSellerReputationObserved());
        assertNotNull(p.getMlProductObservedAt());
        assertNotNull(p.getMlSellerReputationObservedAt());
        assertNull(p.getSeller().getReputationLevel());

        ExtensionImport missing = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston", "199999", "ARS", "Tienda de prueba",
                "MLAU3515247206", "SSD nuevo",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU3515247206", "demo");
        ExtensionProcessingService.applyMercadoLibreObservedEvidence(p, missing);
        assertEquals(57, p.getMlProductReviewCount());
        assertEquals(4, p.getMlSellerReputationObserved());
    }

    @Test void extensionPreviewAlsoIncludesThirdFactor() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston", "199999", "ARS", "Tienda de prueba",
                "MLAU3515247206", "SSD nuevo",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU3515247206", "demo");
        imported.setMlProductReviews(new BigDecimal("4.6"), 57);
        imported.setSellerReputationLevel(4);
        when(repo.findByIdAndImportedBy(42L, "demo")).thenReturn(Optional.of(imported));
        var preview = new ExtensionRiskCoverageService(repo).getCoverage(42L, "demo");
        assertEquals(3, preview.availableFactors());
        assertEquals(3, preview.totalFactors());
        assertTrue(preview.factors().stream().anyMatch(f ->
                "PRODUCT_REVIEWS".equals(f.code()) && f.available()));
    }
}
