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

    private Publication withProductReviews(String rating, Integer reviews, int daysOld) {
        Publication p = listing();
        p.setMlProductRating(rating == null ? null : new BigDecimal(rating));
        p.setMlProductReviewCount(reviews);
        p.setMlProductObservedAt(LocalDateTime.now().minusDays(daysOld));
        return p;
    }

    private void assertProductFactor(RiskEvaluation result, String code, int expectedPoints) {
        assertTrue(result.getFactors().stream().anyMatch(f ->
                code.equals(f.getCode()) && f.getScoreContribution() == expectedPoints),
                "Se esperaba el factor " + code + " con " + expectedPoints + " puntos");
    }

    @Test
    void onlyOfferAndSellerEvidenceCountForMercadoLibreCoverage() {
        Publication p = withProductReviews("4.7", 286, 1);
        p.setMlSellerReputationObserved(5);
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(1));
        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);

        assertEquals(2, result.getAvailableFactors());
        assertEquals(2, result.getTotalFactors());
        assertTrue(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        assertProductFactor(result, "ML_PRODUCT_REVIEWS_OBSERVED", 0);
        assertNull(p.getSeller().getReputationLevel(),
                "La reputación observada no debe simular una corroboración OAuth");
        assertTrue(RiskCoverageCalculator.factors(p).stream()
                .noneMatch(f -> "PRODUCT_REVIEWS".equals(f.code())));
    }

    @Test
    void productReviewsNeverReplaceMissingSellerReputationForLowRisk() {
        Publication p = withProductReviews("4.7", 286, 1);
        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(1, result.getAvailableFactors());
        assertEquals(2, result.getTotalFactors());
        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
        assertTrue(RiskCoverageCalculator.factors(p).stream()
                .anyMatch(f -> f.code().equals("SELLER_REPUTATION") && !f.available()));
    }

    @Test
    void missingOrStaleMetricsNeverCountAsCurrentProductContext() {
        Publication p = withProductReviews("4.7", null, 0);
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        p.setMlProductReviewCount(286);
        p.setMlProductObservedAt(LocalDateTime.now().minusDays(31));
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        p.setMlProductObservedAt(LocalDateTime.now());
        assertTrue(MercadoLibreProductReviewEvidence.hasObservedEvidence(p, LocalDateTime.now()));
        assertFalse(MercadoLibreProductReviewEvidence.hasMetrics(new BigDecimal("0.0"), 286));
        assertFalse(MercadoLibreProductReviewEvidence.hasMetrics(new BigDecimal("4.7"), 0));
    }

    @Test
    void observedThermometerExpiresAndDoesNotBecomeVerifiedLevel() {
        Publication p = listing();
        p.setMlSellerReputationObserved(4);
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(1));
        assertTrue(MercadoLibreSellerEvidence.hasObservedReputation(p, LocalDateTime.now()));
        p.setMlSellerReputationObservedAt(LocalDateTime.now().minusDays(31));
        assertFalse(MercadoLibreSellerEvidence.hasObservedReputation(p, LocalDateTime.now()));
    }

    @Test
    void newImportTransfersObservationsWithoutErasingValidPriorMetrics() {
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

    @Test
    void extensionPreviewAlsoExcludesSharedCatalogRatingFromCoverage() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston", "199999", "ARS", "Tienda de prueba",
                "MLAU3515247206", "SSD nuevo",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU3515247206", "demo");
        imported.setMlProductReviews(new BigDecimal("4.6"), 57);
        imported.setSellerReputationLevel(4);
        when(repo.findByIdAndImportedBy(42L, "demo")).thenReturn(Optional.of(imported));
        var preview = new ExtensionRiskCoverageService(repo).getCoverage(42L, "demo");
        assertEquals(2, preview.availableFactors());
        assertEquals(2, preview.totalFactors());
        assertTrue(preview.factors().stream().noneMatch(f ->
                "PRODUCT_REVIEWS".equals(f.code())));
    }

    @Test
    void fiveStarsAndTwoOpinionsAreInformativeNotRiskPoints() {
        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("5.0", 2, 1));
        assertEquals(0, result.getScore());
        assertEquals(1, result.getAvailableFactors());
        assertEquals(2, result.getTotalFactors());
        assertProductFactor(result, "ML_PRODUCT_REVIEWS_OBSERVED", 0);
        assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", 0);
        assertEquals(2, result.getFactors().size());
    }

    @Test
    void allSampleSizesAreNeutralForOfferRisk() {
        for (int count : new int[] {1, 4, 5, 9, 10, 286}) {
            RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                    .evaluate(withProductReviews("4.7", count, 1));
            assertEquals(0, result.getScore(), "Opiniones del modelo: " + count);
            assertEquals(2, result.getTotalFactors());
            assertProductFactor(result, "ML_PRODUCT_REVIEWS_OBSERVED", 0);
            if (count < 10) assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", 0);
            else assertTrue(result.getFactors().stream().noneMatch(f ->
                    "ML_FEW_PRODUCT_REVIEWS".equals(f.getCode())));
        }
    }

    @Test
    void lowRatingsAreModelContextEvenWithLargeReviewSample() {
        for (int count : new int[] {2, 10, 300}) {
            RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                    .evaluate(withProductReviews("2.4", count, 1));
            assertEquals(0, result.getScore());
            assertProductFactor(result, "ML_LOW_PRODUCT_RATING", 0);
            if (count < 10) assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", 0);
        }
    }

    @Test
    void moderateRatingIsNotEvidenceAgainstIndividualSeller() {
        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("3.5", 9, 1));
        assertEquals(0, result.getScore());
        assertProductFactor(result, "ML_MODERATE_PRODUCT_RATING", 0);
        assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", 0);
    }

    @Test
    void thresholdCodesRemainInformational() {
        RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());
        assertProductFactor(engine.evaluate(withProductReviews("2.9", 10, 1)),
                "ML_LOW_PRODUCT_RATING", 0);
        assertProductFactor(engine.evaluate(withProductReviews("3.0", 10, 1)),
                "ML_MODERATE_PRODUCT_RATING", 0);
        assertProductFactor(engine.evaluate(withProductReviews("4.0", 10, 1)),
                "ML_PRODUCT_REVIEWS_OBSERVED", 0);
    }

    @Test
    void missingExpiredOrOtherMarketplaceMetricsDoNotAddPoints() {
        RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());
        Publication missing = withProductReviews(null, null, 1);
        assertEquals(0, engine.evaluate(missing).getScore());
        assertEquals(1, engine.evaluate(missing).getAvailableFactors());

        Publication expired = withProductReviews("2.0", 2, 31);
        assertEquals(0, engine.evaluate(expired).getScore());
        assertEquals(1, engine.evaluate(expired).getAvailableFactors());

        Publication temu = withProductReviews("2.0", 2, 1);
        temu.setMarketplace(Marketplace.TEMU);
        assertTrue(MercadoLibreProductReviewEvidence.evaluateSignals(
                temu, LocalDateTime.now()).isEmpty());
    }

    @Test
    void confirmedMissingRatingIsInformativeAndDoesNotIncreaseCoverage() {
        Publication p = listing();
        p.setMlSellerReputationObserved(5);
        p.setMlSellerReputationObservedAt(LocalDateTime.now());
        p.setMlProductRatingUnavailable(true);
        p.setMlProductObservedAt(LocalDateTime.now());
        RiskEvaluation evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);

        assertEquals(0, evaluation.getScore());
        assertEquals(2, evaluation.getAvailableFactors());
        assertEquals(2, evaluation.getTotalFactors());
        assertProductFactor(evaluation, "ML_PRODUCT_RATING_UNAVAILABLE", 0);
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(
                p, LocalDateTime.now()));
    }

    @Test
    void unknownOrOldMissingRatingMustRemainNeutral() {
        Publication p = listing();
        p.setMlProductObservedAt(LocalDateTime.now());
        p.setMlProductRatingUnavailable(null);
        assertEquals(0, new RiskEngine(new RiskCoverageCalculator()).evaluate(p).getScore());

        p.setMlProductRatingUnavailable(true);
        p.setMlProductObservedAt(LocalDateTime.now().minusDays(31));
        assertEquals(0, new RiskEngine(new RiskCoverageCalculator()).evaluate(p).getScore());
    }

    @Test
    void newConfirmedAbsenceReplacesPreviouslyMisattributedFourPointNine() {
        Publication p = withProductReviews("4.9", 226, 1);
        ExtensionImport next = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston NV3", "140000", "ARS", "Tienda de prueba",
                "MLAU2874874181", "SSD NVMe de 1 TB",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU2874874181", "demo");
        next.setMlProductRatingUnavailable(true);
        ExtensionProcessingService.applyMercadoLibreObservedEvidence(p, next);
        assertNull(p.getMlProductRating());
        assertNull(p.getMlProductReviewCount());
        assertTrue(p.getMlProductRatingUnavailable());
        assertProductFactor(new RiskEngine(new RiskCoverageCalculator())
                .evaluate(p), "ML_PRODUCT_RATING_UNAVAILABLE", 0);

        next.setMlProductRatingUnavailable(null);
        next.setMlProductReviews(new BigDecimal("4.6"), 16);
        ExtensionProcessingService.applyMercadoLibreObservedEvidence(p, next);
        assertEquals(new BigDecimal("4.6"), p.getMlProductRating());
        assertEquals(16, p.getMlProductReviewCount());
        assertFalse(p.getMlProductRatingUnavailable());
    }

    @Test
    void reviewDescriptionsExplicitlySeparateModelQualityAndSellerTrust() {
        RiskEvaluation high = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("5.0", 2, 1));
        var observed = high.getFactors().stream()
                .filter(f -> "ML_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode()))
                .findFirst().orElseThrow();
        assertTrue(observed.getDescription().contains("No es una calificación de este vendedor"));
        var sample = high.getFactors().stream()
                .filter(f -> "ML_FEW_PRODUCT_REVIEWS".equals(f.getCode()))
                .findFirst().orElseThrow();
        assertTrue(sample.getDescription().contains("no de este vendedor"));

        RiskEvaluation low = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("2.4", 2, 1));
        var lowRating = low.getFactors().stream()
                .filter(f -> "ML_LOW_PRODUCT_RATING".equals(f.getCode()))
                .findFirst().orElseThrow();
        assertTrue(lowRating.getDescription().contains("No evalúa al vendedor"));
        assertEquals(0, lowRating.getScoreContribution());
    }
}
