package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskEvaluationResponse;
import com.marketlens.backend.dto.AutomaticPriceAnalysisResponse;
import com.marketlens.backend.dto.RiskFactorResponse;
import com.marketlens.backend.dto.RiskCoverageFactorResponse;
import com.marketlens.backend.dto.CommunityReportSummaryResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.risk.RiskEngine;
import com.marketlens.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiskEvaluationService {

    private final PublicationRepository publicationRepository;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final RiskEngine riskEngine;
    private final AutomaticPriceAnalysisService priceAnalysisService;
    private final CommunityReportService communityReportService;
    private final MercadoLibreApiService mercadoLibreApiService;

    @Autowired
    public RiskEvaluationService(
            PublicationRepository publicationRepository,
            RiskEvaluationRepository riskEvaluationRepository,
            RiskEngine riskEngine,
            AutomaticPriceAnalysisService priceAnalysisService,
            CommunityReportService communityReportService,
            MercadoLibreApiService mercadoLibreApiService) {
        this.publicationRepository = publicationRepository;
        this.riskEvaluationRepository = riskEvaluationRepository;
        this.riskEngine = riskEngine;
        this.priceAnalysisService = priceAnalysisService;
        this.communityReportService = communityReportService;
        this.mercadoLibreApiService = mercadoLibreApiService;
    }

    /** Constructor de compatibilidad para pruebas con reportes. */
    public RiskEvaluationService(
            PublicationRepository publications,
            RiskEvaluationRepository evaluations,
            RiskEngine engine,
            AutomaticPriceAnalysisService prices,
            CommunityReportService communityReports) {
        this(publications, evaluations, engine, prices, communityReports, null);
    }

    /** Constructor anterior para pruebas sin dependencia de reportes. */
    public RiskEvaluationService(
            PublicationRepository publicationRepository,
            RiskEvaluationRepository riskEvaluationRepository,
            RiskEngine riskEngine,
            AutomaticPriceAnalysisService priceAnalysisService) {
        this(publicationRepository, riskEvaluationRepository, riskEngine, priceAnalysisService, null);
    }

    @Transactional
    public RiskEvaluationResponse evaluate(Long publicationId) {
        return evaluate(publicationId, null);
    }

    /** Usuario autenticado para consultar reseñas acreditadas por OAuth. */
    @Transactional
    public RiskEvaluationResponse evaluate(Long publicationId, String username) {

        Publication publication = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId));

        refreshItemReviews(publication, username);

        Integer pricePoints = null;
        BigDecimal referenceMedian = null;
        int referenceCount = 0;

        // Si todavía no hay un precio válido, el resto de factores de riesgo
        // puede seguir evaluándose de forma independiente.
        // Temu: todavía no hay una muestra comercial comparable y validada.
        // Nunca convertir esto en una penalización de precio automática.
        if (publication.getMarketplace() != com.marketlens.backend.entity.Marketplace.TEMU
                && publication.getPrice() != null
                && publication.getPrice().signum() > 0) {
            AutomaticPriceAnalysisResponse priceAnalysis =
                    priceAnalysisService.analyze(publicationId);
            pricePoints = priceAnalysis.riskPoints();
            referenceMedian = priceAnalysis.referencePriceArs();
            referenceCount = priceAnalysis.referenceCount();
        }

        // El resumen se calcula para esta evaluación y queda reflejado en
        // sus factores persistidos. Reportes posteriores no modifican
        // retroactivamente el puntaje.
        CommunityReportSummaryResponse summary = communityReportService == null
                ? null : communityReportService.getSummary(publicationId);
        int recentReporters = summary == null ? 0 : summary.uniqueReporterCount();

        RiskEvaluation evaluation = riskEngine.evaluate(
                publication, pricePoints, referenceMedian, referenceCount, recentReporters);

        RiskEvaluation savedEvaluation = riskEvaluationRepository.save(evaluation);

        // La explicación corresponde a la evidencia observada en ESTA evaluación.
        return toResponse(savedEvaluation, RiskCoverageCalculator.factors(publication));
    }

    private void refreshItemReviews(Publication publication, String username) {
        if (mercadoLibreApiService == null
                || !MercadoLibreItemReviewEvidence.eligible(publication)
                || username == null || username.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();
        // No repetir una observación concluyente antes de 24 horas.
        // Si la API falla, la verificación sigue inconclusa y puede reintentarse.
        LocalDateTime last = publication.getMlItemReviewObservedAt();
        if (last != null && !last.isAfter(now)
                && last.isAfter(now.minusHours(24))) return;

        try {
            if (!mercadoLibreApiService.hasReviewAccess(username)) return;
            MercadoLibreItemReviewEvidence.Verified verified =
                    mercadoLibreApiService.getVerifiedItemReviews(
                            username, publication.getExternalId());
            if (verified == null) {
                // La API respondió, pero no acreditó reseñas de este ítem.
                publication.setMlItemReviews(null, null, null, now);
            } else {
                publication.setMlItemReviews(verified.itemId(), verified.rating(),
                        verified.count(), now);
            }
            publicationRepository.save(publication);
        } catch (ResponseStatusException exception) {
            // Token vencido, error de red o límite de API: no simular
            // reseñas y no impedir la evaluación de los demás factores.
        }
    }

    private RiskEvaluationResponse toResponse(
            RiskEvaluation evaluation,
            List<RiskCoverageFactorResponse> coverageFactors) {

        List<RiskFactorResponse> factors = evaluation.getFactors()
                .stream()
                .map(factor -> new RiskFactorResponse(
                        factor.getCode(),
                        factor.getDescription(),
                        factor.getScoreContribution()))
                .toList();

        return new RiskEvaluationResponse(
                evaluation.getId(),
                evaluation.getPublication().getId(),
                evaluation.getScore(),
                evaluation.getLevel(),
                evaluation.getEvaluatedAt(),
                factors,

                evaluation.getCoverageStatus(),
                evaluation.getAvailableFactors(),
                evaluation.getTotalFactors(),
                coverageFactors);
    }

    @Transactional(readOnly = true)
    public List<RiskEvaluationResponse> getByPublication(
            Long publicationId) {

        if (!publicationRepository.existsById(publicationId)) {
            throw new ResourceNotFoundException(
                    "Publication not found: " + publicationId);
        }

        return riskEvaluationRepository
                .findByPublicationIdOrderByEvaluatedAtDesc(publicationId)
                .stream()
                // El esquema histórico no almacena el detalle de cada factor:
                // no inferir retroactivamente su disponibilidad actual.
                .map(evaluation -> toResponse(evaluation, null))
                .toList();
    }
}