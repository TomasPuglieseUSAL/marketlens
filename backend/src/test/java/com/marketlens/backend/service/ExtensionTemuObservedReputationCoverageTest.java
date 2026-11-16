package com.marketlens.backend.service;

import com.marketlens.backend.dto.RiskCoverageResponse;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** El resumen de cobertura previo al análisis usa la misma semántica
 * de disponibilidad que la evaluación final de Temu. */
class ExtensionTemuObservedReputationCoverageTest {

    private RiskCoverageResponse preview(String seller, String rating, Boolean observed) {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        ExtensionImport imported = new ExtensionImport(
                "TEMU", "Funda protectora", "9507", "ARS",
                seller, "606892013277333", null,
                "https://www.temu.com/ar-en/funda-g-606892013277333.html",
                "demo");
        imported.setTemuSellerRating(rating == null ? null : new BigDecimal(rating));
        imported.setTemuSellerObservation(null, null, observed);

        when(repo.findByIdAndImportedBy(10L, "demo")).thenReturn(Optional.of(imported));
        return new ExtensionRiskCoverageService(repo).getCoverage(10L, "demo");
    }

    @Test
    void rating48WithoutReviewCountStillCountsAsAvailableReputation() {
        RiskCoverageResponse result = preview("Tokki Shop", "4.8", true);
        assertEquals(1, result.availableFactors());
        assertEquals(3, result.totalFactors());
        assertEquals("PARTIAL", result.status());
        assertTrue(result.factors().stream().anyMatch(f ->
                "SELLER_REPUTATION".equals(f.code()) && f.available()));
    }

    @Test
    void ratingAloneWithoutObservedSellerSectionIsNotCredibleCoverage() {
        assertEquals(0, preview("Tokki Shop", "4.8", null).availableFactors());
        assertEquals(0, preview("Tokki Shop", "4.8", false).availableFactors());
    }

    @Test
    void unknownSellerOrRatingCannotCount() {
        assertEquals(0, preview(null, "4.8", true).availableFactors());
        assertEquals(0, preview("Tokki Shop", null, true).availableFactors());
    }
}
