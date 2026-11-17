package com.marketlens.backend.service;

import com.marketlens.backend.entity.*;
import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MercadoLibreNoApiCoverageTest {

    private Publication item() {
        Publication p = new Publication();
        p.setId(17L);
        p.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setReferenceType("LISTING");
        p.setExternalId("MLA123456789");
        p.setOriginalUrl("https://articulo.mercadolibre.com.ar/MLA-123456789-item-_JM");
        p.setDescription("Artículo usado, detalles de la publicación.");
        Seller seller = new Seller();
        seller.setName("TIENDA");
        seller.setMarketplace(Marketplace.MERCADO_LIBRE);
        seller.setReputationLevel(5);
        p.setSeller(seller);
        return p;
    }

    @Test
    void regularItemHasTwoFactorsWithoutOauth() {
        Publication p = item();
        var result = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(2, result.getAvailableFactors());
        assertEquals(2, result.getTotalFactors());
        assertEquals(RiskCoverageStatus.COMPLETE, result.getCoverageStatus());
        assertTrue(RiskCoverageCalculator.factors(p).stream()
                .noneMatch(f -> "ITEM_REVIEWS".equals(f.code())));
    }

    @Test
    void legacyVerifiedItemReviewsDoNotChangeCoverage() {
        Publication p = item();
        // Registros históricos de V31 no deben restaurar dependencia OAuth.
        p.setMlItemReviews("MLA123456789", new BigDecimal("4.9"), 20, LocalDateTime.now());
        var evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(p);
        assertEquals(2, evaluation.getAvailableFactors());
        assertEquals(2, evaluation.getTotalFactors());
        assertTrue(evaluation.getFactors().stream()
                .noneMatch(f -> "ML_ITEM_REVIEWS_VERIFIED".equals(f.getCode())));
    }

    @Test
    void sharedProductRatingsAreContextNotSellerTrust() {
        Publication p = item();
        p.setPrice(new BigDecimal("2000"));
        p.setMlProductRating(new BigDecimal("4.9"));
        p.setMlProductReviewCount(1552);
        p.setMlProductObservedAt(LocalDateTime.now());
        var evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(
                p, 45, new BigDecimal("100000"), 5);
        assertEquals(45, evaluation.getScore());
        assertEquals(RiskLevel.MEDIUM, evaluation.getLevel());
        assertEquals(2, evaluation.getTotalFactors());
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "ML_PRODUCT_REVIEWS_OBSERVED".equals(f.getCode())
                && f.getScoreContribution() == 0));
    }

    @Test
    void extensionPreviewDoesNotRequirePendingApiReviewFactor() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "Teléfono usado", "100000", "ARS", "VENDEDOR",
                "MLA123456789", "En buen estado",
                "https://articulo.mercadolibre.com.ar/MLA-123456789-item-_JM", "usuario");
        imported.setMarketplaceReference(new MarketplaceReference(
                Marketplace.MERCADO_LIBRE, "LISTING", "MLA123456789"));
        imported.setSellerReputationLevel(5);
        when(repo.findByIdAndImportedBy(42L, "usuario")).thenReturn(Optional.of(imported));
        var preview = new ExtensionRiskCoverageService(repo).getCoverage(42L, "usuario");
        assertEquals(2, preview.availableFactors());
        assertEquals(2, preview.totalFactors());
    }

    @Test
    void evaluatingPublicationDoesNotNeedExternalCredentials() {
        Publication p = item();
        PublicationRepository publications = mock(PublicationRepository.class);
        RiskEvaluationRepository evaluations = mock(RiskEvaluationRepository.class);
        AutomaticPriceAnalysisService prices = mock(AutomaticPriceAnalysisService.class);
        when(publications.findById(17L)).thenReturn(Optional.of(p));
        when(evaluations.save(any(RiskEvaluation.class))).thenAnswer(call -> call.getArgument(0));

        var service = new RiskEvaluationService(
                publications, evaluations,
                new RiskEngine(new RiskCoverageCalculator()), prices);
        var response = service.evaluate(17L);

        assertEquals(2, response.availableFactors());
        assertEquals(2, response.totalFactors());
        verifyNoInteractions(prices);
    }
}
