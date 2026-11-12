package com.marketlens.backend.service;

import com.marketlens.backend.dto.MarketplaceStatisticsResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.ReportRepository;
import com.marketlens.backend.repository.RiskEvaluationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketplaceStatisticsService {

    private final PublicationRepository publicationRepository;
    private final ReportRepository reportRepository;
    private final RiskEvaluationRepository riskEvaluationRepository;

    public MarketplaceStatisticsService(
            PublicationRepository publicationRepository,
            ReportRepository reportRepository,
            RiskEvaluationRepository riskEvaluationRepository) {

        this.publicationRepository = publicationRepository;
        this.reportRepository = reportRepository;
        this.riskEvaluationRepository = riskEvaluationRepository;
    }

    @Transactional(readOnly = true)
    public List<MarketplaceStatisticsResponse> getStatistics() {

        List<RiskEvaluation> latestEvaluations =
            riskEvaluationRepository.findLatestEvaluations();

        List<MarketplaceStatisticsResponse> result =
            new ArrayList<>();

        for (Marketplace marketplace : Marketplace.values()) {

            List<RiskEvaluation> evaluations =
                latestEvaluations.stream()
                    .filter(e ->
                        e.getPublication().getMarketplace() == marketplace
                    )
                    .toList();

            long totalPublications =
                publicationRepository.countByMarketplace(marketplace);

            long totalReports =
                reportRepository.countByMarketplace(marketplace);

            long low = evaluations.stream()
                .filter(e -> e.getLevel() == RiskLevel.LOW)
                .count();

            long medium = evaluations.stream()
                .filter(e -> e.getLevel() == RiskLevel.MEDIUM)
                .count();

            long high = evaluations.stream()
                .filter(e -> e.getLevel() == RiskLevel.HIGH)
                .count();

            double averageScore = evaluations.stream()
                .mapToInt(RiskEvaluation::getScore)
                .average()
                .orElse(0.0);

            result.add(new MarketplaceStatisticsResponse(
                marketplace,
                totalPublications,
                evaluations.size(),
                averageScore,
                low,
                medium,
                high,
                totalReports
            ));
        }

        return result;
    }
}