package com.marketlens.backend.service;

import com.marketlens.backend.entity.*;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RiskEvaluationVerifiedItemReviewsTest {
    private final PublicationRepository publications = mock(PublicationRepository.class);
    private final RiskEvaluationRepository evaluations = mock(RiskEvaluationRepository.class);
    private final AutomaticPriceAnalysisService prices = mock(AutomaticPriceAnalysisService.class);
    private final MercadoLibreApiService reviewsApi = mock(MercadoLibreApiService.class);

    private Publication publication() {
        Publication p = new Publication();
        p.setId(17L);
        p.setMarketplace(Marketplace.MERCADO_LIBRE);
        p.setReferenceType("LISTING");
        p.setExternalId("MLA123456789");
        p.setOriginalUrl("https://articulo.mercadolibre.com.ar/MLA-123456789-telefono-_JM");
        p.setDescription("Teléfono usado");
        Seller seller = new Seller();
        seller.setName("VENDEDOR");
        seller.setMarketplace(Marketplace.MERCADO_LIBRE);
        seller.setReputationLevel(5);
        p.setSeller(seller);
        // Sin precio: no se llama al comparador en estas pruebas.
        return p;
    }

    private RiskEvaluationService service(Publication p) {
        when(publications.findById(17L)).thenReturn(Optional.of(p));
        when(evaluations.save(any(RiskEvaluation.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(publications.save(any(Publication.class)))
                .thenAnswer(call -> call.getArgument(0));
        return new RiskEvaluationService(
                publications, evaluations,
                new RiskEngine(new RiskCoverageCalculator()),
                prices, null, reviewsApi);
    }

    @Test
    void oauthVerifiedItemReviewsRestoreThreeOfThree() {
        Publication p = publication();
        RiskEvaluationService service = service(p);
        when(reviewsApi.hasReviewAccess("demo")).thenReturn(true);
        when(reviewsApi.getVerifiedItemReviews("demo", "MLA123456789"))
                .thenReturn(new MercadoLibreItemReviewEvidence.Verified(
                        "MLA123456789", new BigDecimal("4.8"), 18));

        var result = service.evaluate(17L, "demo");

        assertEquals(0, result.score());
        assertEquals(3, result.availableFactors());
        assertEquals(3, result.totalFactors());
        assertTrue(result.coverageFactors().stream().anyMatch(f ->
                "ITEM_REVIEWS".equals(f.code()) && f.available()));
        assertTrue(result.factors().stream().anyMatch(f ->
                "ML_ITEM_REVIEWS_VERIFIED".equals(f.code())
                        && f.scoreContribution() == 0));
        verify(reviewsApi).getVerifiedItemReviews("demo", "MLA123456789");
    }

    @Test
    void unavailableOAuthDoesNotInventEvidenceOrBreakRiskEvaluation() {
        Publication p = publication();
        RiskEvaluationService service = service(p);

        var result = service.evaluate(17L, "demo");

        assertEquals(2, result.availableFactors());
        assertEquals(3, result.totalFactors());
        assertEquals(0, result.score());
        verify(reviewsApi, never()).getVerifiedItemReviews(any(), any());
    }

    @Test
    void apiFailureDoesNotBlockEvaluationOrConcludeFraud() {
        Publication p = publication();
        RiskEvaluationService service = service(p);
        when(reviewsApi.hasReviewAccess("demo")).thenReturn(true);
        when(reviewsApi.getVerifiedItemReviews("demo", "MLA123456789"))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE, "API no disponible"));

        var result = service.evaluate(17L, "demo");

        assertEquals(2, result.availableFactors());
        assertEquals(3, result.totalFactors());
        assertEquals(0, result.score());
    }

    @Test
    void verifiedCatalogProductReviewsCannotRaiseIndividualCoverage() {
        Publication p = publication();
        RiskEvaluationService service = service(p);
        when(reviewsApi.hasReviewAccess("demo")).thenReturn(true);
        // El verificador rechaza una respuesta con type=product y devuelve null.
        when(reviewsApi.getVerifiedItemReviews("demo", "MLA123456789"))
                .thenReturn(null);
        var result = service.evaluate(17L, "demo");
        assertEquals(2, result.availableFactors());
        assertEquals(3, result.totalFactors());
        assertNull(p.getMlItemReviewVerifiedId());
    }

    @Test
    void userProductsCannotClaimTheThirdItemReviewFactor() {
        Publication p = publication();
        p.setReferenceType("USER_PRODUCT");
        p.setExternalId("MLAU3344351864");
        p.setOriginalUrl("https://www.mercadolibre.com.ar/samsung-a13/up/MLAU3344351864");
        RiskEvaluationService service = service(p);

        var result = service.evaluate(17L, "demo");

        assertEquals(2, result.availableFactors());
        assertEquals(2, result.totalFactors());
        verifyNoInteractions(reviewsApi);
    }
}
