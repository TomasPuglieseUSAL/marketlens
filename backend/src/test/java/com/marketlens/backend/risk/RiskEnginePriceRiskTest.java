package com.marketlens.backend.risk;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.entity.Seller;
import java.time.LocalDateTime;
import com.marketlens.backend.service.RiskCoverageCalculator;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskEnginePriceRiskTest {
    private final RiskEngine engine = new RiskEngine(new RiskCoverageCalculator());

    @Test
    void incorporatesLowPriceFactorIntoScore() {
        Publication publication = basePublication("741000");

        var result = engine.evaluate(publication, 25, new BigDecimal("1499950"), 12);

        assertEquals(25, result.getScore());
        assertTrue(result.getFactors().stream().anyMatch(factor ->
                "UNUSUALLY_LOW_PRICE".equals(factor.getCode())
                        && factor.getScoreContribution() == 25));
    }

    @Test
    void oneVerifiedSalePlusVeryLowPriceGivesFortyAndMediumLevel() {
        Publication publication = basePublication("741000");
        Seller seller = mock(Seller.class);
        when(publication.getSeller()).thenReturn(seller);
        when(publication.getDescription()).thenReturn("Consola usada en buen estado.");
        when(seller.getCompletedSales()).thenReturn(1);
        when(seller.getSalesObservedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        // La edad no está disponible en estos escenarios: Mockito devuelve 0
        // por defecto para Integer y activaría NEW_SELLER_ACCOUNT (+20).
        when(seller.getAccountAgeDays()).thenReturn(null);

        var result = engine.evaluate(publication, 25, new BigDecimal("1499950"), 12);

        assertEquals(40, result.getScore());
        assertEquals(RiskLevel.MEDIUM, result.getLevel());
        assertEquals(2, (int) result.getAvailableFactors());
        assertEquals(3, (int) result.getTotalFactors());
        assertEquals(2, result.getFactors().size());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "LIMITED_SELLER_HISTORY".equals(f.getCode())
                        && f.getScoreContribution() == 15));
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "UNUSUALLY_LOW_PRICE".equals(f.getCode())
                        && f.getScoreContribution() == 25));
    }

    @Test
    void mediumPriceDiscountPlusLimitedHistoryAddsThirtyPoints() {
        Publication publication = basePublication("550000");
        Seller seller = mock(Seller.class);
        when(publication.getSeller()).thenReturn(seller);
        when(publication.getDescription()).thenReturn("Producto usado.");
        when(seller.getCompletedSales()).thenReturn(4);
        when(seller.getSalesObservedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        // La edad no está disponible en estos escenarios: Mockito devuelve 0
        // por defecto para Integer y activaría NEW_SELLER_ACCOUNT (+20).
        when(seller.getAccountAgeDays()).thenReturn(null);

        var result = engine.evaluate(publication, 15, new BigDecimal("1000000"), 5);

        assertEquals(30, result.getScore());
        assertEquals(RiskLevel.MEDIUM, result.getLevel());
    }

    @Test
    void knownNewAccountAddsTwentyPointsOnTopOfPriceAndSales() {
        Publication publication = basePublication("741000");
        Seller seller = mock(Seller.class);
        when(publication.getSeller()).thenReturn(seller);
        when(publication.getDescription()).thenReturn("Consola usada en buen estado.");
        when(seller.getCompletedSales()).thenReturn(1);
        when(seller.getSalesObservedAt()).thenReturn(LocalDateTime.now().minusHours(1));
        when(seller.getAccountAgeDays()).thenReturn(10);

        var result = engine.evaluate(publication, 25, new BigDecimal("1499950"), 12);

        assertEquals(60, result.getScore());
        assertEquals(RiskLevel.HIGH, result.getLevel());
        assertEquals(3, result.getFactors().size());
        assertTrue(result.getFactors().stream().anyMatch(f ->
                "NEW_SELLER_ACCOUNT".equals(f.getCode())
                        && f.getScoreContribution() == 20));
    }

    @Test
    void normalPriceAddsNoFactor() {
        var result = engine.evaluate(basePublication("1500000"),
                0, new BigDecimal("1400000"), 14);
        assertEquals(0, result.getScore());
        assertTrue(result.getFactors().stream().noneMatch(factor ->
                "UNUSUALLY_LOW_PRICE".equals(factor.getCode())));
    }

    @Test
    void insufficientReferencesDoNotAddPoints() {
        var result = engine.evaluate(basePublication("741000"),
                null, new BigDecimal("1499950"), 4);
        assertEquals(0, result.getScore());
    }

    private Publication basePublication(String price) {
        Publication publication = mock(Publication.class);
        when(publication.getPrice()).thenReturn(new BigDecimal(price));
        when(publication.getMarketplace()).thenReturn(Marketplace.MERCADO_LIBRE);
        return publication;
    }
}
