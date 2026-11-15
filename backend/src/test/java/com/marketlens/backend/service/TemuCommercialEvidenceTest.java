package com.marketlens.backend.service;

import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.risk.RiskEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TemuCommercialEvidenceTest {

    private Publication publication(String tenure, String sold) {
        Publication p = new Publication();
        p.setMarketplace(Marketplace.TEMU);
        p.setDescription("Funda magnética para teléfono");
        Seller seller = new Seller();
        seller.setMarketplace(Marketplace.TEMU);
        seller.setName("Tokki Shop");
        p.setSeller(seller);
        p.setTemuStoreTenureText(tenure);
        p.setTemuStoreTenureObservedAt(LocalDateTime.now().minusHours(1));
        p.setTemuProductSoldText(sold);
        p.setTemuProductSalesObservedAt(LocalDateTime.now().minusHours(1));
        return p;
    }

    @Test
    void parsesApproximateSalesWithoutInventingExactSellerSales() {
        var fifteenThousand = TemuCommercialEvidence.parseProductSales("15K+ sold");
        assertNotNull(fifteenThousand);
        assertEquals(15000, fifteenThousand.minimumUnits());
        assertTrue(fifteenThousand.approximate());
        assertEquals(300000,
                TemuCommercialEvidence.parseProductSales("300K+ Sold").minimumUnits());
        assertEquals(3, TemuCommercialEvidence.parseProductSales("3 sold").minimumUnits());
        assertFalse(TemuCommercialEvidence.parseProductSales("3 sold").approximate());
        assertNull(TemuCommercialEvidence.parseProductSales("unknown sold"));
        assertNull(TemuCommercialEvidence.parseProductSales("15K+ store reviews"));
        assertNull(TemuCommercialEvidence.parseProductSales("300M+ sold"));
    }

    @Test
    void shortSellerTenureAddsRiskWithoutExactRegistrationDate() {
        var veryNew = TemuCommercialEvidence.evaluateStoreAge(
                publication("6 days on Temu", null), LocalDateTime.now());
        assertEquals("TEMU_VERY_RECENT_STORE", veryNew.code());
        assertEquals(15, veryNew.points());

        var week = TemuCommercialEvidence.evaluateStoreAge(
                publication("1 week on Temu", null), LocalDateTime.now());
        assertEquals("TEMU_RECENT_STORE", week.code());
        assertEquals(8, week.points());

        var underWeek = TemuCommercialEvidence.evaluateStoreAge(
                publication("under one week on Temu", null), LocalDateTime.now());
        assertEquals(15, underWeek.points());

        var month = TemuCommercialEvidence.evaluateStoreAge(
                publication("1 month on Temu", null), LocalDateTime.now());
        assertEquals(0, month.points());

        var fourWeeks = TemuCommercialEvidence.evaluateStoreAge(
                publication("4 weeks on Temu", null), LocalDateTime.now());
        assertEquals(0, fourWeeks.points());

        var year = TemuCommercialEvidence.evaluateStoreAge(
                publication("1 year on Temu", null), LocalDateTime.now());
        assertEquals(0, year.points());
    }

    @Test
    void ageObservationExpiresAndDoesNotDoubleCountOfficialAccountAge() {
        Publication old = publication("3 days on Temu", null);
        old.setTemuStoreTenureObservedAt(LocalDateTime.now().minusDays(31));
        assertNull(TemuCommercialEvidence.evaluateStoreAge(old, LocalDateTime.now()));

        Publication recentlyObserved = publication("6 days on Temu", null);
        recentlyObserved.setTemuStoreTenureObservedAt(LocalDateTime.now().minusDays(2));
        assertEquals(8, TemuCommercialEvidence.evaluateStoreAge(
                recentlyObserved, LocalDateTime.now()).points());

        Publication officialAge = publication("2 days on Temu", null);
        officialAge.getSeller().setAccountAgeDays(2);
        assertEquals(0, TemuCommercialEvidence.evaluateStoreAge(
                officialAge, LocalDateTime.now()).points());
    }

    @Test
    void lowProductSalesAreOnlyMildSignalsWithoutExistingProductReviews() {
        assertEquals(5, TemuCommercialEvidence.evaluateProductSales(
                publication("1 year on Temu", "3 sold"), LocalDateTime.now()).points());
        assertEquals(3, TemuCommercialEvidence.evaluateProductSales(
                publication("1 year on Temu", "6 sold"), LocalDateTime.now()).points());
        assertEquals(0, TemuCommercialEvidence.evaluateProductSales(
                publication("1 year on Temu", "15K+ sold"), LocalDateTime.now()).points());

        Publication reviews = publication("1 year on Temu", "3 sold");
        reviews.setTemuProductRating(new BigDecimal("4.7"));
        reviews.setTemuProductReviewCount(286);
        reviews.setTemuProductObservedAt(LocalDateTime.now().minusHours(1));
        assertEquals(0, TemuCommercialEvidence.evaluateProductSales(
                reviews, LocalDateTime.now()).points());
    }

    @Test
    void storeSalesAreSeparateAndDoNotDuplicateSmallProductSales() {
        Publication listing = publication("1 year on Temu", "3 sold");
        listing.setTemuStoreSoldText("3 sold");
        listing.setTemuStoreSalesObservedAt(LocalDateTime.now().minusHours(1));
        var evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(listing);
        assertEquals(10, evaluation.getScore());
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "TEMU_VERY_FEW_STORE_SALES".equals(f.getCode())
                        && f.getScoreContribution() == 10));
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "TEMU_PRODUCT_SALES_OBSERVED".equals(f.getCode())
                        && f.getScoreContribution() == 0));

        listing.setTemuStoreSoldText("300K+ Sold");
        assertEquals(0, TemuCommercialEvidence.evaluateStoreSales(
                listing, LocalDateTime.now()).points());
        listing.setTemuStoreSoldText("3 sold");
        listing.setTemuSellerReviewCount(8);
        assertEquals(0, TemuCommercialEvidence.evaluateStoreSales(
                listing, LocalDateTime.now()).points());
    }

    @Test
    void commercialSignalsDoNotChangeThreeCoverageDimensions() {
        Publication listing = publication("3 days on Temu", "3 sold");
        var evaluation = new RiskEngine(new RiskCoverageCalculator()).evaluate(listing);
        assertEquals(1, evaluation.getAvailableFactors());
        assertEquals(3, evaluation.getTotalFactors());
        assertEquals(20, evaluation.getScore());
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "TEMU_VERY_RECENT_STORE".equals(f.getCode())
                        && f.getScoreContribution() == 15));
        assertTrue(evaluation.getFactors().stream().anyMatch(f ->
                "TEMU_VERY_FEW_PRODUCT_SALES".equals(f.getCode())
                        && f.getScoreContribution() == 5));
    }

    @Test
    void newObservationRefreshesOnlyAvailableFieldsAndRespectsSellerIdentity() {
        Publication listing = publication(null, null);
        ExtensionImport incoming = new ExtensionImport("TEMU", "Funda", "9507",
                "ARS", "Tokki Shop", "606892013277333", "Funda",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        incoming.setTemuCommercialObservation("1 year on Temu", "15K+ sold", "300K+ Sold");
        ExtensionProcessingService.applyTemuCommercialEvidence(listing, incoming);
        assertEquals("1 year on Temu", listing.getTemuStoreTenureText());
        assertEquals("15K+ sold", listing.getTemuProductSoldText());
        assertEquals("300K+ Sold", listing.getTemuStoreSoldText());
        assertNotNull(listing.getTemuStoreTenureObservedAt());
        assertNotNull(listing.getTemuProductSalesObservedAt());

        var firstStoreObservedAt = listing.getTemuStoreTenureObservedAt();
        var firstProductObservedAt = listing.getTemuProductSalesObservedAt();
        ExtensionImport empty = new ExtensionImport("TEMU", "Funda", "9507",
                "ARS", "Tokki Shop", "606892013277333", "Funda",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        ExtensionProcessingService.applyTemuCommercialEvidence(listing, empty);
        assertEquals(firstStoreObservedAt, listing.getTemuStoreTenureObservedAt());
        assertEquals(firstProductObservedAt, listing.getTemuProductSalesObservedAt());

        ExtensionImport otherStore = new ExtensionImport("TEMU", "Funda", "9507",
                "ARS", "Other Shop", "606892013277333", "Funda",
                "https://www.temu.com/goods.html?goods_id=606892013277333", "demo");
        otherStore.setTemuCommercialObservation("2 days on Temu", "4 sold", "2 sold");
        ExtensionProcessingService.applyTemuCommercialEvidence(listing, otherStore);
        assertEquals("1 year on Temu", listing.getTemuStoreTenureText());
        assertEquals("4 sold", listing.getTemuProductSoldText());
        assertEquals("300K+ Sold", listing.getTemuStoreSoldText());
    }
}
