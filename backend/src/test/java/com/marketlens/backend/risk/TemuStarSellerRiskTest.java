package com.marketlens.backend.risk;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.service.RiskCoverageCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TemuStarSellerRiskTest {

    @Test
    void observedSellerRatingAddsReputationCoverageButNotArbitraryRiskPoints() {
        Seller seller = new Seller();
        seller.setName("Tokki Shop");
        seller.setMarketplace(Marketplace.TEMU);

        Publication publication = new Publication();
        publication.setMarketplace(Marketplace.TEMU);
        publication.setTitle("Funda protectora");
        publication.setDescription("Funda para teléfono.");
        publication.setPrice(new BigDecimal("9507.00"));
        publication.setSeller(seller);
        publication.setTemuSellerRating(new BigDecimal("4.8"));

        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication);

        assertEquals(2, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getLevel());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "TEMU_SELLER_RATING_OBSERVED".equals(f.getCode())
                && f.getScoreContribution() == 0));
    }

    @Test
    void observedBadgeDoesNotLowerRiskOrCountAsVerifiedReputation() {
        Seller seller = new Seller();
        seller.setName("Tokki Shop");
        seller.setMarketplace(Marketplace.TEMU);

        Publication publication = new Publication();
        publication.setMarketplace(Marketplace.TEMU);
        publication.setTitle("Funda para teléfono");
        publication.setPrice(new BigDecimal("9507.00"));
        publication.setDescription("Funda protectora para teléfono.");
        publication.setSeller(seller);
        publication.setSellerBadge("TEMU_STAR_SELLER");

        RiskEvaluation result = new RiskEngine(new RiskCoverageCalculator())
                .evaluate(publication);

        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
        assertEquals(1, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(0, result.getScore());
        assertTrue(result.getFactors().stream().anyMatch(factor ->
                "TEMU_STAR_SELLER_OBSERVED".equals(factor.getCode())
                && factor.getScoreContribution() == 0));
    }
}
