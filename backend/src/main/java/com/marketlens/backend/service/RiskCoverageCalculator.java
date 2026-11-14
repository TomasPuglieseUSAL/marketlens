package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskCoverageStatus;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.Seller;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class RiskCoverageCalculator {

    // La tercera dimensión sólo aplica donde se extraen opiniones de producto.
    private static int totalFactors(Publication publication) {
        return publication.getMarketplace() == Marketplace.TEMU ? 3 : 2;
    }

    public void applyCoverage(
            Publication publication,
            RiskEvaluation evaluation) {

        int available = countAvailableFactors(publication);
        int total = totalFactors(publication);

        RiskCoverageStatus status;

        if (available == total) {
            status = RiskCoverageStatus.COMPLETE;

        } else if (available == 0) {
            status = RiskCoverageStatus.INSUFFICIENT;

        } else {
            status = RiskCoverageStatus.PARTIAL;
        }

        evaluation.setCoverageStatus(status);
        evaluation.setAvailableFactors(available);
        evaluation.setTotalFactors(total);
    }

    private int countAvailableFactors(Publication publication) {

        int available = 0;

        // 1. Información de producto.
        String description = publication.getDescription();
        if (description != null && !description.isBlank()) available++;

        // 2. Reputación o historial comercial observado del vendedor.
        Seller seller = publication.getSeller();
        if (seller != null) {
            Integer reputationLevel = seller.getReputationLevel();
            BigDecimal reputation = seller.getReputation();
            boolean hasReputationLevel = reputationLevel != null
                    && reputationLevel >= 1 && reputationLevel <= 5;
            boolean hasLegacyReputation = reputation != null
                    && reputation.compareTo(BigDecimal.ZERO) >= 0
                    && reputation.compareTo(BigDecimal.ONE) <= 0;
            boolean hasCommercialEvidence = SellerActivityEvidence.detect(
                    seller, LocalDateTime.now())
                    != SellerActivityEvidence.ActivitySignal.NONE;
            if (hasReputationLevel || hasLegacyReputation || hasCommercialEvidence ||
                    TemuSellerRiskScoring.hasObservedReputation(publication, LocalDateTime.now())) {
                available++;
            }
        }

        // 3. Opiniones del producto: disponible para Temu.
        // No usar la edad de la cuenta como requisito de cobertura.
        if (TemuProductReviewEvidence.hasObservedEvidence(publication, LocalDateTime.now())) {
            available++;
        }
        return available;
    }

}