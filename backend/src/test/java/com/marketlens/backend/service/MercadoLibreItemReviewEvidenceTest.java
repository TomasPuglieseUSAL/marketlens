package com.marketlens.backend.service;

import com.marketlens.backend.entity.*;
import com.marketlens.backend.repository.ExtensionImportRepository;
import java.util.Optional;
import static org.mockito.Mockito.*;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MercadoLibreItemReviewEvidenceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private Publication regularItem() {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setReferenceType("LISTING");
        p.setExternalId("MLA123456789");
        p.setOriginalUrl("https://articulo.mercadolibre.com.ar/MLA-123456789-telefono-_JM");
        p.setDescription("Teléfono usado en buen estado.");
        Seller seller = new Seller();
        seller.setMarketplace(Marketplace.MERCADO_LIBRE);
        seller.setName("TIENDA_PRUEBA");
        seller.setReputationLevel(5);
        p.setSeller(seller);
        return p;
    }

    private String response(String objectType, String objectId, String status) {
        return """
                {
                  "paging": {"total": 18},
                  "rating_average": 4.8,
                  "cross_site_enabled": false,
                  "reviews": [{
                    "id": 125,
                    "reviewable_object": {"type": "%s", "id": "%s"},
                    "status": "%s",
                    "rate": 5
                  }]
                }
                """.formatted(objectType, objectId, status);
    }

    @Test
    void verifiesOnlyPublishedReviewsOfExactItem() {
        var valid = MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(response("item", "MLA123456789", "published")));
        assertNotNull(valid);
        assertEquals("MLA123456789", valid.itemId());
        assertEquals(new BigDecimal("4.8"), valid.rating());
        assertEquals(18, valid.count());
    }

    @Test
    void rejectsCatalogProductEvenWhenItsReviewsWereRequestedThroughItemEndpoint() {
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(response("product", "MLA123456789", "published"))));
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(response("item", "MLA999999999", "published"))));
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(response("item", "MLA123456789", "pending"))));
    }

    @Test
    void rejectsMixedReviewObjectsAndInvalidAggregates() {
        String mixed = """
                {"paging":{"total":2},"rating_average":4.5,
                 "reviews":[
                   {"status":"published","reviewable_object":{"type":"item","id":"MLA123456789"}},
                   {"status":"published","reviewable_object":{"type":"product","id":"MLA123456789"}}
                 ]}
                """;
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(mixed)));
        String noSample = """
                {"paging":{"total":100},"rating_average":4.5,"reviews":[]}
                """;
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(noSample)));
        String invalid = """
                {"paging":{"total":1},"rating_average":5.2,"reviews":[
                 {"status":"published","reviewable_object":{"type":"item","id":"MLA123456789"}}]}
                """;
        assertNull(MercadoLibreItemReviewEvidence.fromApiResponse(
                "MLA123456789", mapper.readTree(invalid)));
    }

    @Test
    void regularItemHasThirdFactorOnlyWhenVerifiedAndFresh() {
        Publication p = regularItem();
        RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());

        var withoutReviews = engine.evaluate(p);
        assertEquals(2, withoutReviews.getAvailableFactors());
        assertEquals(3, withoutReviews.getTotalFactors());

        p.setMlItemReviews("MLA123456789", new BigDecimal("4.8"), 18, LocalDateTime.now());
        var verified = engine.evaluate(p);
        assertEquals(3, verified.getAvailableFactors());
        assertEquals(3, verified.getTotalFactors());
        assertEquals(0, verified.getScore());
        assertTrue(verified.getFactors().stream().anyMatch(f ->
                "ML_ITEM_REVIEWS_VERIFIED".equals(f.getCode())
                && f.getScoreContribution() == 0));

        p.setMlItemReviews("MLA999999999", new BigDecimal("4.8"), 18, LocalDateTime.now());
        assertEquals(2, engine.evaluate(p).getAvailableFactors());

        p.setMlItemReviews("MLA123456789", new BigDecimal("4.8"), 18,
                LocalDateTime.now().minusDays(31));
        assertEquals(2, engine.evaluate(p).getAvailableFactors());
    }

    @Test
    void catalogOfferAndUserProductDoNotBorrowRatingForIndividualCoverage() {
        Publication p = regularItem();
        p.setMlProductRating(new BigDecimal("4.7"));
        p.setMlProductReviewCount(286);
        p.setMlProductObservedAt(LocalDateTime.now());
        p.setMlItemReviews("MLA123456789", new BigDecimal("4.8"), 18, LocalDateTime.now());

        p.setOriginalUrl("https://www.mercadolibre.com.ar/samsung/p/MLA24356923");
        assertEquals(2, new RiskCoverageCalculator().factors(p).size());

        p.setOriginalUrl("https://www.mercadolibre.com.ar/samsung-a13/up/MLAU3344351864");
        p.setReferenceType("USER_PRODUCT");
        p.setExternalId("MLAU3344351864");
        assertEquals(2, new RiskCoverageCalculator().factors(p).size());
        assertFalse(MercadoLibreItemReviewEvidence.hasVerifiedEvidence(p, LocalDateTime.now()));
    }

    @Test
    void previewOnlyAdmitsIndividualReviewFactorForRegularItemIds() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "Teléfono usado", "100000", "ARS", "VENDEDOR",
                "MLA123456789", "En buen estado",
                "https://articulo.mercadolibre.com.ar/MLA-123456789-telefono-_JM", "demo");
        imported.setMarketplaceReference(new MarketplaceReference(
                Marketplace.MERCADO_LIBRE, "LISTING", "MLA123456789"));
        imported.setSellerReputationLevel(5);
        when(repo.findByIdAndImportedBy(42L, "demo")).thenReturn(Optional.of(imported));

        var preview = new ExtensionRiskCoverageService(repo).getCoverage(42L, "demo");

        assertEquals(2, preview.availableFactors());
        assertEquals(3, preview.totalFactors());
        assertTrue(preview.factors().stream().anyMatch(f ->
                "ITEM_REVIEWS".equals(f.code()) && !f.available()));
    }

    @Test
    void positiveVerifiedReviewsDoNotCancelExtremePriceAndLimitedSellerHistory() {
        Publication p = regularItem();
        p.setPrice(new BigDecimal("2000"));
        p.getSeller().setReputationLevel(null);
        p.getSeller().setCompletedSales(1);
        p.getSeller().setSalesObservedAt(LocalDateTime.now());
        p.setMlItemReviews("MLA123456789", new BigDecimal("4.9"), 90, LocalDateTime.now());
        var result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(p, 45, new BigDecimal("100000"), 5);

        assertEquals(60, result.getScore());
        assertEquals(RiskLevel.HIGH, result.getLevel());
        assertEquals(3, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "ML_ITEM_REVIEWS_VERIFIED".equals(f.getCode())
                && f.getScoreContribution() == 0));
    }

    @Test
    void sharedProductRatingsDoNotCompleteAnUnverifiedItemReviewFactor() {
        Publication p = regularItem();
        p.setMlProductRating(new BigDecimal("4.9"));
        p.setMlProductReviewCount(1552);
        p.setMlProductObservedAt(LocalDateTime.now());

        var evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(2, evaluation.getAvailableFactors());
        assertEquals(3, evaluation.getTotalFactors());
        assertEquals(0, evaluation.getScore());
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "ML_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode())));
        assertTrue(RiskCoverageCalculator.factors(p).stream().anyMatch(f ->
                "ITEM_REVIEWS".equals(f.code()) && !f.available()));
    }
}
