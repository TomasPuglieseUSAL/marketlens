package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class TemuSellerRiskScoringTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 11, 14, 18, 0);

    private Publication listing(String rating, Integer count, Boolean inspected, boolean hasStoreId) {
        Publication publication = new Publication();
        publication.setMarketplace(Marketplace.TEMU);
        Seller seller = new Seller();
        seller.setName("Tienda de prueba");
        seller.setMarketplace(Marketplace.TEMU);
        publication.setSeller(seller);
        publication.setTemuSellerRating(rating == null ? null : new BigDecimal(rating));
        publication.setTemuSellerReviewCount(count);
        publication.setTemuStoreId(hasStoreId ? "613456789012345" : null);
        publication.setTemuSellerSectionObserved(inspected);
        publication.setTemuSellerObservedAt(now.minusDays(1));
        return publication;
    }

    @Test void unknownRatingAfterObservedSellerSectionAddsSmallRisk() {
        var result = TemuSellerRiskScoring.evaluate(listing(null,null,true,false), now);
        assertEquals("TEMU_SELLER_REPUTATION_UNAVAILABLE",result.code());
        assertEquals(5,result.points());
    }
    @Test void inabilityToObserveSellerSectionDoesNotAddPoints() {
        assertNull(TemuSellerRiskScoring.evaluate(listing(null,null,false,false),now));
        assertNull(TemuSellerRiskScoring.evaluate(listing(null,null,null,false),now));
    }
    @Test void zeroConfirmedReviewsIsNotZeroSales() {
        var signal = TemuSellerRiskScoring.evaluate(listing(null,0,true,false),now);
        assertEquals(10,signal.points());
        assertTrue(signal.description().contains("no cero ventas"));
    }
    @Test void fewReviewsAreNotDoublePenalizedForRating() {
        assertEquals(10,TemuSellerRiskScoring.evaluate(listing("2.1",3,true,true),now).points());
        assertEquals(5,TemuSellerRiskScoring.evaluate(listing("2.1",8,true,true),now).points());
    }
    @Test void enoughReviewsEnableLowRatingRisk() {
        assertEquals(15,TemuSellerRiskScoring.evaluate(listing("2.8",50,true,true),now).points());
        assertEquals(5,TemuSellerRiskScoring.evaluate(listing("3.8",50,true,true),now).points());
        assertNull(TemuSellerRiskScoring.evaluate(listing("4.8",50,true,true),now));
    }
    @Test void trustedCoverageRequiresStoreIdAndReviewCount() {
        assertFalse(TemuSellerRiskScoring.hasSupportedReputation(listing("4.8",null,true,true),now));
        assertFalse(TemuSellerRiskScoring.hasSupportedReputation(listing("4.8",55,true,false),now));
        assertTrue(TemuSellerRiskScoring.hasSupportedReputation(listing("4.8",55,true,true),now));
    }
    @Test void oldEvidenceDoesNotPenalize() {
        var publication = listing(null,null,true,true);
        publication.setTemuSellerObservedAt(now.minusDays(31));
        assertNull(TemuSellerRiskScoring.evaluate(publication,now));
    }
}
