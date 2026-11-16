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

    private Publication withProductReviews(String rating, Integer reviews, int daysOld) {
        Publication publication = listing();
        publication.setMlProductRating(rating == null ? null : new BigDecimal(rating));
        publication.setMlProductReviewCount(reviews);
        publication.setMlProductObservedAt(LocalDateTime.now().minusDays(daysOld));
        return publication;
    }

    private void assertProductFactor(RiskEvaluation result, String code, int expectedPoints) {
        assertTrue(result.getFactors().stream().anyMatch(f ->
                code.equals(f.getCode()) && f.getScoreContribution() == expectedPoints),
                "Se esperaba el factor " + code + " con +" + expectedPoints);
    }

    @Test void fiveStarsAndTwoOpinionsOnlyAddFourPointsForSmallSample() {
        Publication publication = withProductReviews("5.0", 2, 1);
        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator()).evaluate(publication);
        assertEquals(4, result.getScore());
        assertEquals(2, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertProductFactor(result, "ML_PRODUCT_REVIEWS_OBSERVED", 0);
        assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", 4);
        assertEquals(2, result.getFactors().size());
    }

    @Test void countThresholdsDoNotInventReviewsOrPenalizeLargerSamples() {
        for (int[] example : new int[][] {{1, 4}, {4, 4}, {5, 2}, {9, 2}, {10, 0}, {286, 0}}) {
            Publication publication = withProductReviews("4.7", example[0], 1);
            RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator()).evaluate(publication);
            assertEquals(example[1], result.getScore(), "Recuento " + example[0]);
            assertEquals(3, result.getTotalFactors());
            if (example[1] > 0) assertProductFactor(result, "ML_FEW_PRODUCT_REVIEWS", example[1]);
            else assertTrue(result.getFactors().stream().noneMatch(f ->
                    "ML_FEW_PRODUCT_REVIEWS".equals(f.getCode())));
        }
    }

    @Test void lowRatingIsDiscountedWithFewReviewsAndAddsUpToSamplePenalty() {
        RiskEvaluation small = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("2.4", 2, 1));
        assertEquals(9, small.getScore());
        assertProductFactor(small, "ML_LOW_PRODUCT_RATING", 5);
        assertProductFactor(small, "ML_FEW_PRODUCT_REVIEWS", 4);

        RiskEvaluation enough = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("2.4", 10, 1));
        assertEquals(15, enough.getScore());
        assertProductFactor(enough, "ML_LOW_PRODUCT_RATING", 15);
        assertEquals(1, enough.getFactors().size());
    }

    @Test void intermediateRatingUsesSmallerWeightsForSmallSamples() {
        RiskEvaluation small = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("3.5", 9, 1));
        assertEquals(5, small.getScore());
        assertProductFactor(small, "ML_MODERATE_PRODUCT_RATING", 3);
        assertProductFactor(small, "ML_FEW_PRODUCT_REVIEWS", 2);

        RiskEvaluation enough = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("3.5", 10, 1));
        assertEquals(6, enough.getScore());
        assertProductFactor(enough, "ML_MODERATE_PRODUCT_RATING", 6);
        assertEquals(1, enough.getFactors().size());
    }

    @Test void exactRatingThresholdsHaveNoOverlap() {
        RiskEvaluation below = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("2.9", 10, 1));
        RiskEvaluation equalToThree = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("3.0", 10, 1));
        RiskEvaluation equalToFour = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("4.0", 10, 1));
        assertEquals(15, below.getScore());
        assertEquals(6, equalToThree.getScore());
        assertEquals(0, equalToFour.getScore());
    }

    @Test void missingExpiredOrOtherMarketplaceMetricsDoNotAddPoints() {
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

    @Test void confirmedMissingRatingAddsOnlyTwoPointsWithoutIncreasingCoverage() {
        Publication publication = listing();
        publication.setMlSellerReputationObserved(5);
        publication.setMlSellerReputationObservedAt(LocalDateTime.now());
        publication.setMlProductRatingUnavailable(true);
        publication.setMlProductObservedAt(LocalDateTime.now());

        RiskEvaluation evaluation = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication);

        assertEquals(2, evaluation.getScore());
        assertEquals(2, evaluation.getAvailableFactors());
        assertEquals(3, evaluation.getTotalFactors());
        assertProductFactor(evaluation, "ML_PRODUCT_RATING_UNAVAILABLE", 2);
        assertFalse(MercadoLibreProductReviewEvidence.hasObservedEvidence(
                publication, LocalDateTime.now()));
    }

    @Test void unknownOrOldMissingRatingMustRemainNeutral() {
        Publication publication = listing();
        publication.setMlProductObservedAt(LocalDateTime.now());
        publication.setMlProductRatingUnavailable(null);
        assertEquals(0, new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication).getScore());

        publication.setMlProductRatingUnavailable(true);
        publication.setMlProductObservedAt(LocalDateTime.now().minusDays(31));
        assertEquals(0, new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication).getScore());
    }

    @Test void newConfirmedAbsenceReplacesPreviouslyMisattributedFourPointNine() {
        Publication publication = listing();
        publication.setMlProductRating(new BigDecimal("4.9"));
        publication.setMlProductReviewCount(226);
        publication.setMlProductObservedAt(LocalDateTime.now().minusDays(1));

        ExtensionImport newObservation = new ExtensionImport("MERCADO_LIBRE",
                "SSD Kingston NV3", "140000", "ARS", "Tienda de prueba",
                "MLAU2874874181", "SSD NVMe de 1 TB",
                "https://www.mercadolibre.com.ar/ssd/up/MLAU2874874181", "demo");
        newObservation.setMlProductRatingUnavailable(true);

        ExtensionProcessingService.applyMercadoLibreObservedEvidence(
                publication, newObservation);

        assertNull(publication.getMlProductRating());
        assertNull(publication.getMlProductReviewCount());
        assertTrue(publication.getMlProductRatingUnavailable());
        assertProductFactor(new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication), "ML_PRODUCT_RATING_UNAVAILABLE", 2);

        // Las observaciones positivas posteriores pueden reemplazar el estado.
        newObservation.setMlProductRatingUnavailable(null);
        newObservation.setMlProductReviews(new BigDecimal("4.6"), 16);
        ExtensionProcessingService.applyMercadoLibreObservedEvidence(
                publication, newObservation);
        assertEquals(new BigDecimal("4.6"), publication.getMlProductRating());
        assertEquals(16, publication.getMlProductReviewCount());
        assertFalse(publication.getMlProductRatingUnavailable());
    }

    @Test void reviewDescriptionsAreConciseAndDoNotRepeatOtherSignals() {
        var fiveStars = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("5.0", 2, 1));
        assertEquals("Calificación del producto: 5,0/5.",
                fiveStars.getFactors().stream()
                        .filter(f -> "ML_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode()))
                        .findFirst().orElseThrow().getDescription());
        assertEquals("Cantidad de opiniones reducida: 2.",
                fiveStars.getFactors().stream()
                        .filter(f -> "ML_FEW_PRODUCT_REVIEWS".equals(f.getCode()))
                        .findFirst().orElseThrow().getDescription());

        var lowStars = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(withProductReviews("2.4", 2, 1));
        assertEquals("Calificación baja del producto: 2,4/5.",
                lowStars.getFactors().stream()
                        .filter(f -> "ML_LOW_PRODUCT_RATING".equals(f.getCode()))
                        .findFirst().orElseThrow().getDescription());
        assertEquals("Cantidad de opiniones reducida: 2.",
                lowStars.getFactors().stream()
                        .filter(f -> "ML_FEW_PRODUCT_REVIEWS".equals(f.getCode()))
                        .findFirst().orElseThrow().getDescription());

        assertTrue(fiveStars.getFactors().stream().allMatch(f ->
                !f.getDescription().contains("fiabilidad del vendedor")
                        && !f.getDescription().contains("por sí sola")));
    }

}
