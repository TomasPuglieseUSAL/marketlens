package com.marketlens.backend.service;

import com.marketlens.backend.dto.PriceCandidateResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.repository.PublicationRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AutomaticPriceAnalysisServiceTest {

    @Test
    void calculatesPricePenaltyWhenAtLeastFiveDistinctOffersExist() {
        Fixture fixture = new Fixture();
        fixture.add(1, "MLA1", "1100000");
        fixture.add(2, "MLA2", "1300000");
        fixture.add(3, "MLA3", "1499950");
        fixture.add(4, "MLA4", "1500000");
        fixture.add(5, "MLA5", "1950000");

        var result = fixture.analyze();

        assertEquals(5, result.referenceCount());
        assertEquals(0, result.referencePriceArs().compareTo(new BigDecimal("1499950")));
        assertEquals(25, result.riskPoints());
    }

    @Test
    void countsTwoImportsOfTheSameExternalOfferOnlyOnce() {
        Fixture fixture = new Fixture();
        fixture.add(1, "MLA1", "1100000");
        fixture.add(2, "mla1", "1110000");
        fixture.add(3, "MLA3", "1300000");
        fixture.add(4, "MLA4", "1499950");
        fixture.add(5, "MLA5", "1500000");

        var result = fixture.analyze();

        assertEquals(4, result.referenceCount());
        assertNull(result.riskPoints());
        assertEquals(4, result.referenceIds().size());
        assertTrue(result.candidates().stream().anyMatch(candidate ->
                candidate.reason().contains("misma oferta externa")));
    }

    @Test
    void doesNotTreatTheTargetImportAsAnIndependentReference() {
        Fixture fixture = new Fixture();
        fixture.add(1, "TARGET", "1300000");
        fixture.add(2, "MLA2", "1300000");

        var result = fixture.analyze();

        assertEquals(1, result.referenceCount());
        assertEquals(List.of(2L), result.referenceIds());
        assertNull(result.riskPoints());
    }

    private static final class Fixture {
        final PublicationRepository publicationRepository = mock(PublicationRepository.class);
        final PriceComparisonService comparisonService = mock(PriceComparisonService.class);
        final GenericProductMatchingService matchingService = mock(GenericProductMatchingService.class);
        final Publication target = mock(Publication.class);
        final List<Publication> publications = new ArrayList<>();
        final List<PriceCandidateResponse> candidates = new ArrayList<>();

        Fixture() {
            when(target.getId()).thenReturn(70L);
            when(target.getPrice()).thenReturn(new BigDecimal("741000"));
            when(target.getMarketplace()).thenReturn(Marketplace.MERCADO_LIBRE);
            when(target.getExternalId()).thenReturn("TARGET");
            when(publicationRepository.findById(70L)).thenReturn(Optional.of(target));
            when(matchingService.compare(eq(target), any(Publication.class), anyDouble()))
                    .thenReturn(new GenericProductMatchingService.MatchDecision(
                            "PROVISIONAL_MATCH", "Coincidencia provisional"));
        }

        void add(long id, String externalId, String price) {
            Publication publication = mock(Publication.class);
            when(publication.getId()).thenReturn(id);
            when(publication.getMarketplace()).thenReturn(Marketplace.MERCADO_LIBRE);
            when(publication.getExternalId()).thenReturn(externalId);
            publications.add(publication);

            PriceCandidateResponse candidate = mock(PriceCandidateResponse.class);
            when(candidate.publicationId()).thenReturn(id);
            when(candidate.title()).thenReturn("Producto comparable");
            when(candidate.marketplace()).thenReturn("MERCADO_LIBRE");
            when(candidate.price()).thenReturn(new BigDecimal(price));
            when(candidate.similarity()).thenReturn(0.5);
            candidates.add(candidate);
        }

        com.marketlens.backend.dto.AutomaticPriceAnalysisResponse analyze() {
            when(comparisonService.findCandidates(eq(70L), anyInt())).thenReturn(candidates);
            when(publicationRepository.findAllById(any())).thenReturn(publications);
            return new AutomaticPriceAnalysisService(
                    publicationRepository, comparisonService, matchingService).analyze(70L);
        }
    }
}
