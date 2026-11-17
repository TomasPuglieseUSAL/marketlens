package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskCoverageResponse;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemuProductReviewEvidenceTest {
    private Publication listing(String rating, Integer reviews) {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.TEMU);
        p.setDescription("Funda protectora");
        Seller s = new Seller();
        s.setName("Tokki Shop");
        s.setMarketplace(Marketplace.TEMU);
        p.setSeller(s);
        p.setTemuSellerRating(new BigDecimal("4.8"));
        p.setTemuSellerSectionObserved(true);
        p.setTemuSellerObservedAt(LocalDateTime.now().minusDays(1));
        p.setTemuProductRating(rating == null ? null : new BigDecimal(rating));
        p.setTemuProductReviewCount(reviews);
        p.setTemuProductObservedAt(LocalDateTime.now().minusDays(1));
        return p;
    }

    @Test void rating47With286ReviewsAndPositiveSellerAllowsLowRiskWithFullCoverage() {
        Publication p = listing("4.7", 286);
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(3, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(0, result.getScore());
        // LOW clasifica la ausencia de señales relevantes; no confirma
        // autenticidad ni convierte reseñas en ventas verificadas.
        assertEquals(RiskLevel.LOW, result.getLevel());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "TEMU_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode())
                        && f.getScoreContribution() == 0));
    }

    @Test void lowRatingWithSufficientReviewsAddsRisk() {
        Publication p = listing("2.1", 286);
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(15, result.getScore());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "TEMU_LOW_PRODUCT_RATING".equals(f.getCode())
                        && f.getScoreContribution() == 15));
    }

    @Test void smallSampleDoesNotCreateLowRatingPenalty() {
        Publication p = listing("2.1", 2);
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(0, result.getScore());
        assertEquals(3, result.getAvailableFactors());
    }

    @Test void partialOrExpiredProductMetricsDoNotCount() {
        Publication p = listing("4.7", null);
        assertFalse(TemuProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        assertEquals(2, new RiskEngine(new RiskCoverageCalculator())
                .evaluate(p).getAvailableFactors());
        p.setTemuProductReviewCount(286);
        p.setTemuProductObservedAt(LocalDateTime.now().minusDays(31));
        assertFalse(TemuProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
    }

    @Test void importsAndEvaluationsUseSameThirdFactor() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport incoming = new ExtensionImport("TEMU", "Funda", "9507", "ARS",
                "Tokki Shop", "606892013277333", "Funda protectora",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        incoming.setTemuSellerRating(new BigDecimal("4.8"));
        incoming.setTemuSellerObservation(null, null, true);
        incoming.setTemuProductReviews(new BigDecimal("4.7"), 286);
        when(repo.findByIdAndImportedBy(10L, "demo")).thenReturn(Optional.of(incoming));

        RiskCoverageResponse preview = new ExtensionRiskCoverageService(repo)
                .getCoverage(10L, "demo");
        assertEquals(3, preview.availableFactors());
        assertEquals(3, preview.totalFactors());
        assertTrue(preview.factors().stream().anyMatch(f ->
                "PRODUCT_REVIEWS".equals(f.code()) && f.available()));
    }

    @Test void existingPublicationReceivesNewObservationWithoutClearingOnMissingData() {
        Publication p = listing(null, null);
        ExtensionImport first = new ExtensionImport("TEMU", "Funda", "9507", "ARS",
                "Tokki Shop", "606892013277333", "Funda",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        first.setTemuProductReviews(new BigDecimal("4.7"), 286);
        ExtensionProcessingService.applyTemuProductEvidence(p, first);
        assertEquals(286, p.getTemuProductReviewCount());
        assertNotNull(p.getTemuProductObservedAt());

        ExtensionImport missing = new ExtensionImport("TEMU", "Funda", "9507", "ARS",
                "Tokki Shop", "606892013277333", "Funda",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        ExtensionProcessingService.applyTemuProductEvidence(p, missing);
        assertEquals(new BigDecimal("4.7"), p.getTemuProductRating());
        assertEquals(286, p.getTemuProductReviewCount());
    }

    @Test void accountAgeStillPenalizesButIsNotARequiredCoverageFactor() {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setDescription("Artículo");
        Seller s = new Seller();
        s.setAccountAgeDays(10);
        p.setSeller(s);
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(1, result.getAvailableFactors());
        // Las opiniones generales del modelo ya no cuentan para la
        // cobertura de Mercado Libre: descripción y vendedor (2 factores).
        assertEquals(2, result.getTotalFactors());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "NEW_SELLER_ACCOUNT".equals(f.getCode())
                        && f.getScoreContribution() == 20));
    }

    @Test void temuRetainsThreeCoverageFactorsWithoutMakingAccountAgeMandatory() {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.TEMU);
        p.setDescription("Funda protectora");
        Seller s = new Seller();
        s.setMarketplace(Marketplace.TEMU);
        s.setAccountAgeDays(10);
        p.setSeller(s);

        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);

        // La descripción está disponible; la reputación de tienda y las
        // reseñas del producto no lo están, y la antigüedad no añade cobertura.
        assertEquals(1, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "NEW_SELLER_ACCOUNT".equals(f.getCode())
                        && f.getScoreContribution() == 20));
    }
}
