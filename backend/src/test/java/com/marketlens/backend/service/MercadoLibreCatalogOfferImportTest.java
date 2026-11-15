package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLibreCatalogOfferImportTest {

    private static final String CATALOG_URL =
            "https://www.mercadolibre.com.ar/disco-solido-ssd-kingston-kc600-512gb-color-gris-oscuro/p/MLA24356923";

    private ExtensionPreviewRequest request(String offerId, String externalId) {
        return new ExtensionPreviewRequest(
                "MERCADO_LIBRE", "Kingston KC600 512 GB", "451.989", "ARS",
                "MEXX GAMERS POR NATURALEZA", externalId, "SSD SATA 512 GB",
                CATALOG_URL, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null, null, null,
                new BigDecimal("4.7"), 286, offerId);
    }

    @Test void selectedOfferIsStoredWithOriginalCatalogUrl() {
        ExtensionImportRepository repository = mock(ExtensionImportRepository.class);
        when(repository.save(any(ExtensionImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ExtensionPreviewRequest request = request("MLA3007753206", "MLA3007753206");
        ExtensionImportService service = new ExtensionImportService(repository);
        service.importPublication(request, "demo");
        var captor = org.mockito.ArgumentCaptor.forClass(ExtensionImport.class);
        verify(repository).save(captor.capture());
        assertEquals("MLA3007753206", captor.getValue().getSelectedOfferId());
        assertEquals(CATALOG_URL, captor.getValue().getOriginalUrl());
        assertEquals("MEXX GAMERS POR NATURALEZA", captor.getValue().getSellerName());
    }

    @Test void selectedOfferNormalizesToListingAndPriceMatchesSelectedRow() {
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "Kingston KC600 512 GB", "451.989", "ARS",
                "MEXX GAMERS POR NATURALEZA", "MLA3007753206",
                "SSD SATA 512 GB", CATALOG_URL, "demo");
        imported.setSelectedOfferId("MLA3007753206");

        ExtensionImportRepository repository = mock(ExtensionImportRepository.class);
        when(repository.findByIdAndImportedBy(7L, "demo")).thenReturn(Optional.of(imported));
        var normalized = new ExtensionNormalizationService(repository).normalize(7L, "demo");
        assertEquals("LISTING", normalized.referenceType());
        assertEquals("MLA3007753206", normalized.referenceId());
        // BigDecimal.equals compara también la escala (451989 vs 451989.00).
        // Para importes monetarios, comprobar la igualdad numérica.
        assertEquals(0, new BigDecimal("451989").compareTo(normalized.price()));
        assertFalse(normalized.warnings().stream().anyMatch(w ->
                w.contains("no coincide con el de la URL")));
    }

    @Test void inconsistentSelectionIsRejectedBeforeStorage() {
        ExtensionImportRepository repository = mock(ExtensionImportRepository.class);
        ExtensionImportService service = new ExtensionImportService(repository);
        assertThrows(ResponseStatusException.class, () ->
                service.importPublication(request("MLA1659974940", "MLA3007753206"), "demo"));
        verify(repository, never()).save(any());
    }

    @Test void bareCatalogRemainsCatalogRatherThanBecomingOffer() {
        ExtensionImport imported = new ExtensionImport("MERCADO_LIBRE",
                "Kingston KC600 512 GB", "451.989", "ARS",
                "MEXX GAMERS POR NATURALEZA", "MLA24356923",
                "SSD SATA 512 GB", CATALOG_URL, "demo");
        ExtensionImportRepository repository = mock(ExtensionImportRepository.class);
        when(repository.findByIdAndImportedBy(8L, "demo")).thenReturn(Optional.of(imported));
        var normalized = new ExtensionNormalizationService(repository).normalize(8L, "demo");
        assertEquals("CATALOG_PRODUCT", normalized.referenceType());
        assertEquals("MLA24356923", normalized.referenceId());
    }
}
