package com.marketlens.backend.service;

import com.marketlens.backend.dto.DashboardSummaryResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.ReportRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;
import com.marketlens.backend.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private final PublicationRepository publicationRepository;
    private final SellerRepository sellerRepository;
    private final RiskEvaluationRepository riskEvaluationRepository;
    private final ReportRepository reportRepository;

    public DashboardService(
            PublicationRepository publicationRepository,
            SellerRepository sellerRepository,
            RiskEvaluationRepository riskEvaluationRepository,
            ReportRepository reportRepository) {

        this.publicationRepository = publicationRepository;
        this.sellerRepository = sellerRepository;
        this.riskEvaluationRepository =
            riskEvaluationRepository;
        this.reportRepository = reportRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {

        long totalPublications =
            publicationRepository.count();

        long totalSellers =
            sellerRepository.count();

        long totalEvaluations =
            riskEvaluationRepository.count();

        long totalReports =
            reportRepository.count();

        List<RiskEvaluation> latestEvaluations =
            riskEvaluationRepository.findLatestEvaluations();

        double averageRiskScore =
            calculateAverageRiskScore(latestEvaluations);

        Map<String, Long> evaluationsByRiskLevel =
            countByRiskLevel(latestEvaluations);

        Map<String, Long> publicationsByMarketplace =
            countByMarketplace();

        return new DashboardSummaryResponse(
            totalPublications,
            totalSellers,
            totalEvaluations,
            totalReports,
            averageRiskScore,
            evaluationsByRiskLevel,
            publicationsByMarketplace
        );
    }

    private double calculateAverageRiskScore(
            List<RiskEvaluation> evaluations) {

        if (evaluations.isEmpty()) {
            return 0.0;
        }

        return evaluations
            .stream()
            .mapToInt(RiskEvaluation::getScore)
            .average()
            .orElse(0.0);
    }

    private Map<String, Long> countByRiskLevel(
            List<RiskEvaluation> evaluations) {

        Map<String, Long> result =
            new LinkedHashMap<>();

        for (RiskLevel level : RiskLevel.values()) {
            result.put(level.name(), 0L);
        }

        for (RiskEvaluation evaluation : evaluations) {

            String level =
                evaluation.getLevel().name();

            result.put(
                level,
                result.get(level) + 1
            );
        }

        return result;
    }

    private Map<String, Long> countByMarketplace() {

        Map<String, Long> result =
            new LinkedHashMap<>();

        for (Marketplace marketplace :
                Marketplace.values()) {

            result.put(
                marketplace.name(),
                0L
            );
        }

        publicationRepository
            .findAll()
            .forEach(publication -> {

                String marketplace =
                    publication
                        .getMarketplace()
                        .name();

                result.put(
                    marketplace,
                    result.get(marketplace) + 1
                );
            });

        return result;
    }
}