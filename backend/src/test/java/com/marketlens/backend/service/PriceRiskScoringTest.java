package com.marketlens.backend.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PriceRiskScoringTest {
    private static final BigDecimal MEDIAN = new BigDecimal("1000000");

    @Test
    void returnsNullWhenSampleIsTooSmall() {
        assertNull(PriceRiskScoring.score(new BigDecimal("100000"), MEDIAN, 4));
    }

    @Test
    void returnsNullForInvalidPrices() {
        assertNull(PriceRiskScoring.score(null, MEDIAN, 10));
        assertNull(PriceRiskScoring.score(BigDecimal.ZERO, MEDIAN, 10));
        assertNull(PriceRiskScoring.score(BigDecimal.ONE, BigDecimal.ZERO, 10));
    }

    @Test
    void respectsThresholdBoundaries() {
        assertEquals(0, PriceRiskScoring.score(new BigDecimal("700000"), MEDIAN, 5));
        assertEquals(5, PriceRiskScoring.score(new BigDecimal("699999.99"), MEDIAN, 5));
        assertEquals(5, PriceRiskScoring.score(new BigDecimal("600000"), MEDIAN, 5));
        assertEquals(15, PriceRiskScoring.score(new BigDecimal("599999.99"), MEDIAN, 5));
        assertEquals(15, PriceRiskScoring.score(new BigDecimal("500000"), MEDIAN, 5));
        assertEquals(25, PriceRiskScoring.score(new BigDecimal("499999.99"), MEDIAN, 5));
    }

    @Test
    void checksIntermediateBandWithoutRoundingToUpperBand() {
        assertEquals(15, PriceRiskScoring.score(new BigDecimal("550000"), MEDIAN, 5));
        assertEquals(5, PriceRiskScoring.score(new BigDecimal("650000"), MEDIAN, 5));
        assertEquals(0, PriceRiskScoring.score(new BigDecimal("900000"), MEDIAN, 5));
        assertNull(PriceRiskScoring.score(new BigDecimal("100000"), MEDIAN, 4));
    }

    @Test
    void matchesTwoRealMarketlensExamples() {
        assertEquals(25, PriceRiskScoring.score(
                new BigDecimal("741000"), new BigDecimal("1499950"), 12));
        assertEquals(0, PriceRiskScoring.score(
                new BigDecimal("1500000"), new BigDecimal("1400000"), 14));
    }
}
