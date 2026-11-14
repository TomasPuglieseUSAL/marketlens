package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskEvaluationResponse;
import com.marketlens.backend.dto.AutomaticPriceAnalysisResponse;
import com.marketlens.backend.dto.RiskFactorResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.risk.RiskEngine;
import com.marketlens.backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.math.BigDecimal;

@Service
public class RiskEvaluationService {

    private final PublicationRepository publicationRepository;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final RiskEngine riskEngine;
    private final AutomaticPriceAnalysisService priceAnalysisService;

    public RiskEvaluationService(
            PublicationRepository publicationRepository,
            RiskEvaluationRepository riskEvaluationRepository,
            RiskEngine riskEngine,
            AutomaticPriceAnalysisService priceAnalysisService) {

        this.publicationRepository = publicationRepository;
        this.riskEvaluationRepository = riskEvaluationRepository;
        this.riskEngine = riskEngine;
        this.priceAnalysisService = priceAnalysisService;
    }

    @Transactional
    public RiskEvaluationResponse evaluate(Long publicationId) {

        Publication publication = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId));

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

        RiskEvaluation evaluation = riskEngine.evaluate(
                publication, pricePoints, referenceMedian, referenceCount);

        RiskEvaluation savedEvaluation = riskEvaluationRepository.save(evaluation);

        return toResponse(savedEvaluation);
    }

    private RiskEvaluationResponse toResponse(
            RiskEvaluation evaluation) {

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
                evaluation.getTotalFactors());
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
                .map(this::toResponse)
                .toList();
    }
}