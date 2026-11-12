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

    private static final int TOTAL_FACTORS = 3;

    public void applyCoverage(
            Publication publication,
            RiskEvaluation evaluation) {

        int available = countAvailableFactors(publication);

        RiskCoverageStatus status;

        if (available == TOTAL_FACTORS) {
            status = RiskCoverageStatus.COMPLETE;

        } else if (available == 0) {
            status = RiskCoverageStatus.INSUFFICIENT;

        } else {
            status = RiskCoverageStatus.PARTIAL;
        }

        evaluation.setCoverageStatus(status);
        evaluation.setAvailableFactors(available);
        evaluation.setTotalFactors(TOTAL_FACTORS);
    }

    private int countAvailableFactors(Publication publication) {

        int available = 0;

        // -------------------------------------
        // 1. Descripción de la publicación
        // -------------------------------------

        String description = publication.getDescription();

        if (description != null && !description.isBlank()) {
            available++;
        }

        Seller seller = publication.getSeller();

        if (seller == null) {
            return available;
        }

        // -------------------------------------
        // 2. Reputación del vendedor
        // -------------------------------------

        Integer reputationLevel = seller.getReputationLevel();

        BigDecimal reputation = seller.getReputation();

        boolean hasReputationLevel = reputationLevel != null &&
                reputationLevel >= 1 &&
                reputationLevel <= 5;

        boolean hasLegacyReputation = reputation != null &&
                reputation.compareTo(BigDecimal.ZERO) >= 0 &&
                reputation.compareTo(BigDecimal.ONE) <= 0;

        // Una observación comercial verificable también cubre esta dimensión:
        // ventas numéricas, ventas insuficientes declaradas por la fuente,
        // o un catálogo pequeño observado recientemente.
        boolean hasCommercialEvidence = SellerActivityEvidence.detect(
                seller, LocalDateTime.now())
                != SellerActivityEvidence.ActivitySignal.NONE;

        if (hasReputationLevel ||
                hasLegacyReputation ||
                hasCommercialEvidence) {
            available++;
        }

        // -------------------------------------
        // 3. Antigüedad de la cuenta
        // -------------------------------------

        Integer accountAgeDays = seller.getAccountAgeDays();

        boolean hasExactAccountAge = accountAgeDays != null &&
                accountAgeDays >= 0;

        Integer facebookJoinYear = publication.getFacebookJoinYear();

        boolean hasFacebookJoinYear = publication.getMarketplace() == Marketplace.FACEBOOK_MARKETPLACE &&
                facebookJoinYear != null &&
                facebookJoinYear >= 2004 &&
                facebookJoinYear <= java.time.Year.now().getValue();

        if (hasExactAccountAge || hasFacebookJoinYear) {
            available++;
        }

        return available;
    }

}