package com.marketlens.backend.service;

import com.marketlens.backend.entity.Publication;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicationSimilarityServiceTest {

    private final PublicationSimilarityService similarity = new PublicationSimilarityService();

    @Test
    void informativeDescriptionsBoostShortTitleMatch() {
        Publication target = publication(1, "Samsung A13 (Usado)",
                "Samsung Galaxy A13 de 64 GB con 4 GB RAM.");
        Publication candidate = publication(2,
                "Samsung Galaxy A13 64gb Negro 4gb Ram (Usado)",
                "Samsung Galaxy A13 de 64 GB con 4 GB RAM.");
        double withDescription = similarity.calculateSimilarity(target, candidate);
        double titleOnly = similarity.calculateSimilarity(
                publication(1, target.getTitle(), ""),
                publication(2, candidate.getTitle(), ""));
        assertTrue(withDescription > titleOnly,
                "La coincidencia informativa de la descripción debería sumar señal");
        assertTrue(withDescription < 1.0);
    }

    @Test
    void missingDescriptionPreservesTitleOnlyScore() {
        Publication target = publication(1, "Samsung A13 (Usado)", "A13, 64 GB");
        Publication candidate = publication(2,
                "Samsung Galaxy A13 64gb Negro 4gb Ram (Usado)", "");
        double partiallyDescribed = similarity.calculateSimilarity(target, candidate);
        double titlesOnly = similarity.calculateSimilarity(
                publication(1, target.getTitle(), ""),
                publication(2, candidate.getTitle(), ""));
        assertEquals(titlesOnly, partiallyDescribed, 1e-9);
    }

    @Test
    void onlyReadsBoundedDescriptionPrefix() {
        String prefix = "Samsung Galaxy A13 64 GB 4 GB RAM. ".repeat(20);
        Publication target = publication(1, "Samsung A13 (Usado)", prefix + "Texto A");
        Publication candidate = publication(2,
                "Samsung Galaxy A13 64gb Negro 4gb Ram (Usado)", prefix + "Texto B");
        double original = similarity.calculateSimilarity(target, candidate);
        double changedTail = similarity.calculateSimilarity(
                publication(1, target.getTitle(), prefix + "Texto cambiado despues del limite"),
                candidate);
        assertEquals(original, changedTail, 1e-9);
    }

    @Test
    void twoUnrelatedTitlesDoNotBecomeIdenticalBecauseDescriptionsMatch() {
        Publication target = publication(1, "Samsung A13 (Usado)",
                "Envio gratuito y consultas al vendedor.");
        Publication candidate = publication(2, "Auriculares Bluetooth (Usado)",
                "Envio gratuito y consultas al vendedor.");
        double score = similarity.calculateSimilarity(target, candidate);
        assertTrue(score < 0.5, "El titulo debe dominar el puntaje de similitud");
    }

    private Publication publication(long id, String title, String description) {
        Publication publication = mock(Publication.class);
        when(publication.getId()).thenReturn(id);
        when(publication.getTitle()).thenReturn(title);
        when(publication.getDescription()).thenReturn(description);
        return publication;
    }
}
