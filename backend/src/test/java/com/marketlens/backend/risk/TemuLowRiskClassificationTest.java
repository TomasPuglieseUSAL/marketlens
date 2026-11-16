package com.marketlens.backend.risk;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.service.RiskCoverageCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TemuLowRiskClassificationTest {
    private final RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());

    private Publication listing(String sellerRating, String productRating, Integer productReviews) {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.TEMU);
        p.setTitle("Funda protectora");
        p.setDescription("Funda para teléfono con protección magnética.");
        p.setPrice(new BigDecimal("9507.00"));

        Seller seller = new Seller();
        seller.setMarketplace(Marketplace.TEMU);
        seller.setName("Tokki Shop");
        p.setSeller(seller);

        p.setTemuSellerRating(new BigDecimal(sellerRating));
        p.setTemuSellerSectionObserved(true);
        p.setTemuSellerObservedAt(LocalDateTime.now().minusHours(1));
        if (productRating != null && productReviews != null) {
            p.setTemuProductRating(new BigDecimal(productRating));
            p.setTemuProductReviewCount(productReviews);
            p.setTemuProductObservedAt(LocalDateTime.now().minusHours(1));
        }
        return p;
    }

    @Test void completePositiveTemuEvidenceWithZeroPointsIsLow() {
        Publication p = listing("4.8", "4.7", 286);
        p.setTemuProductSoldText("15K+ sold");
        p.setTemuProductSalesObservedAt(LocalDateTime.now().minusHours(1));
        p.setTemuStoreSoldText("300K+ Sold");
        p.setTemuStoreSalesObservedAt(LocalDateTime.now().minusHours(1));
        p.setTemuStoreTenureText("1 year on Temu");
        p.setTemuStoreTenureObservedAt(LocalDateTime.now().minusHours(1));

        var result = engine.evaluate(p);
        assertEquals(3, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getLevel());
    }

    @Test void completeEvidenceDoesNotRequireStoreIdOrExactStoreReviewCountForLow() {
        Publication p = listing("4.8", "4.7", 286);
        assertNull(p.getTemuStoreId());
        assertNull(p.getTemuSellerReviewCount());
        var result = engine.evaluate(p);
        assertEquals(3, result.getAvailableFactors());
        assertEquals(0, result.getScore());
        assertEquals(RiskLevel.LOW, result.getLevel());
    }

    @Test void sellerRatingAloneDoesNotAllowLowWhenCoverageIncomplete() {
        var result = engine.evaluate(listing("4.8", null, null));
        assertEquals(2, result.getAvailableFactors());
        assertEquals(3, result.getTotalFactors());
        assertEquals(0, result.getScore());
        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
    }

    @Test void insufficientProductReviewSampleCannotBeClassifiedLow() {
        var result = engine.evaluate(listing("4.8", "4.7", 3));
        assertEquals(3, result.getAvailableFactors());
        assertEquals(0, result.getScore());
        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
    }

    @Test void weakRatingsWithoutStoreReviewSampleRemainIndeterminate() {
        var poorSeller = engine.evaluate(listing("2.8", "4.7", 286));
        assertEquals(3, poorSeller.getAvailableFactors());
        assertEquals(0, poorSeller.getScore());
        assertEquals(RiskLevel.INDETERMINATE, poorSeller.getLevel());

        var poorProduct = engine.evaluate(listing("4.8", "3.7", 286));
        assertEquals(3, poorProduct.getAvailableFactors());
        assertTrue(poorProduct.getScore() > 0);
        assertEquals(RiskLevel.INDETERMINATE, poorProduct.getLevel());
    }

    @Test void expiredProductReviewsKeepLowRiskClassificationBlocked() {
        Publication p = listing("4.8", "4.7", 286);
        p.setTemuProductObservedAt(LocalDateTime.now().minusDays(31));
        var result = engine.evaluate(p);
        assertEquals(2, result.getAvailableFactors());
        assertEquals(RiskLevel.INDETERMINATE, result.getLevel());
    }
}
