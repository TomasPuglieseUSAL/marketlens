package com.marketlens.backend.service;

import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Publication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TemuDescriptionBackfillTest {

    private ExtensionImport importedWithDescription(String description) {
        return new ExtensionImport("TEMU", "Funda", "9507", "ARS",
                "Tokki Shop", "606892013277333", description,
                "https://www.temu.com/goods.html?goods_id=606892013277333", "test-user");
    }

    @Test
    void fillsMissingDescriptionWhenTemuPublicationIsReused() {
        Publication publication = new Publication();
        publication.setDescription(null);
        ExtensionProcessingService.fillMissingTemuDescription(
                publication, importedWithDescription("Feature: Wireless Charging"));
        assertEquals("Feature: Wireless Charging", publication.getDescription());
    }

    @Test
    void keepsExistingDescription() {
        Publication publication = new Publication();
        publication.setDescription("Descripción anterior");
        ExtensionProcessingService.fillMissingTemuDescription(
                publication, importedWithDescription("Descripción nueva"));
        assertEquals("Descripción anterior", publication.getDescription());
    }

    @Test
    void doesNotInventDescriptionWhenIncomingIsBlank() {
        Publication publication = new Publication();
        ExtensionProcessingService.fillMissingTemuDescription(
                publication, importedWithDescription("   "));
        assertNull(publication.getDescription());
    }
}
