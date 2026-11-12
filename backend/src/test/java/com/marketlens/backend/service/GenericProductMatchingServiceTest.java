package com.marketlens.backend.service;

import com.marketlens.backend.entity.Publication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericProductMatchingServiceTest {

    private final GenericProductMatchingService matcher = new GenericProductMatchingService();

    @Test
    void acceptsEquivalentStorageUnitsAndAlphanumericSeparators() {
        assertStatus("PROVISIONAL_MATCH",
                "Consola PX5 Digital 1TB nueva", "",
                "Consola PX 5 Digital 1000 GB nueva", "", 0.6);
    }

    @Test
    void acceptsMatchingProductsWithoutNumbers() {
        assertStatus("PROVISIONAL_MATCH", "Mesa plegable nueva", "",
                "Mesa plegable nueva", "", 0.6);
    }

    @Test
    void doesNotEquateDifferentExplicitDescriptors() {
        assertStatus("INSUFFICIENT_DATA", "Cámara Alpha X7 compacta nueva", "",
                "Cámara Alpha X7 profesional nueva", "", 0.6);
    }

    @Test
    void detectsExplicitConditionMismatch() {
        assertStatus("NOT_COMPARABLE", "Auriculares inalámbricos usados", "",
                "Auriculares inalámbricos nuevos", "", 0.6);
    }

    @Test
    void detectsCapacityMismatch() {
        assertStatus("NOT_COMPARABLE", "Disco externo 1TB nuevo", "",
                "Disco externo 2TB nuevo", "", 0.6);
    }

    @Test
    void rejectsUnknownBundleComposition() {
        assertStatus("INSUFFICIENT_DATA", "Combo cámara lentes nuevo", "",
                "Combo cámara lentes nuevo", "", 0.6);
    }

    @Test
    void rejectsSwappedQuantityAssociations() {
        assertStatus("INSUFFICIENT_DATA", "Cámara 2 baterías 3 cargadores nueva", "",
                "Cámara 3 baterías 2 cargadores nueva", "", 0.6);
    }

    @Test
    void rejectsReversedAlphanumericCode() {
        assertStatus("INSUFFICIENT_DATA", "Producto A54 nuevo", "",
                "Producto 54A nuevo", "", 0.6);
    }

    @Test
    void detectsPackagingConflict() {
        assertStatus("NOT_COMPARABLE", "Cámara compacta sin caja nueva", "",
                "Cámara compacta con caja nueva", "", 0.6);
    }

    @Test
    void doesNotInventCondition() {
        assertStatus("INSUFFICIENT_DATA", "Mesa plegable", "",
                "Mesa plegable", "", 0.6);
    }

    // Casos derivados de la muestra real que compartiste (publicacion 65).
    @Test
    void acceptsSimilarTextsAtLowerTfIdfScore() {
        assertStatus("PROVISIONAL_MATCH", targetTitle(), targetDescription(),
                "Playstation 5 Slim Edición Digital 1tb (Usado)", "Aviso legal", 0.156187);
    }

    @Test
    void acceptsStorageInCandidateDescription() {
        assertStatus("PROVISIONAL_MATCH", targetTitle(), targetDescription(),
                "Sony Playstation 5 Slim Digital (Usado)",
                "Consola de sobremesa con diseño compacto. Su edición digital evita discos. "
                + "Con una capacidad de 1 TB, ofrece espacio para guardar títulos.", 0.343936);
    }

    @Test
    void acceptsExtraNoncontradictoryTitleTerms() {
        assertStatus("PROVISIONAL_MATCH", targetTitle(), targetDescription(),
                "Sony Playstation 5 Slim Digital 1tb Color Blanco (Usado)",
                "Sin garantía", 0.248775);
    }

    @Test
    void rejectsRealCapacityConflict() {
        assertStatus("NOT_COMPARABLE", targetTitle(), targetDescription(),
                "Playstation 5 Slim 825 Gb Digital (Usado)", "", 0.2047);
    }

    @Test
    void rejectsWeaklyRelatedDifferentModel() {
        assertStatus("INSUFFICIENT_DATA", targetTitle(), targetDescription(),
                "Sony Ps 5 Pro (Usado)", "", 0.08966);
    }

    @Test
    void acceptsPartialAcronymOverlapWithoutProductDictionary() {
        assertStatus("PROVISIONAL_MATCH", targetTitle(), targetDescription(),
                "Ps5 Slim Digital Sony (Usado)", "Voltaje: 100-240V", 0.14422);
    }

    @Test
    void stillRejectsBundles() {
        assertStatus("INSUFFICIENT_DATA", targetTitle(), targetDescription(),
                "Sony Playstation 5 Slim 1tb Digital Bundle Ratchet & Clank (Usado)",
                "", 0.1838);
    }

    @Test
    void rejectsDifferentNumericModel() {
        assertStatus("INSUFFICIENT_DATA", "Playstation 5 Usada", "",
                "Playstation 1 Usada", "", 0.35);
    }

    @Test
    void rejectsCompetingVariantDescriptors() {
        assertStatus("INSUFFICIENT_DATA", "Cámara Compacta X7 Usada", "",
                "Cámara Profesional X7 Usada", "", 0.6);
    }

    @Test
    void retainsLowSimilarityNoiseFloor() {
        assertStatus("INSUFFICIENT_DATA", "Consola PX5 Digital 1TB nueva", "",
                "Consola PX 5 Digital 1000 GB nueva", "", 0.07);
    }

    private String targetTitle() {
        return "Sony Playstation 5 Slim 1t Usado Como Nuevo (Usado)";
    }

    private String targetDescription() {
        return "Está como nueva sin detalles estéticos incluye un juego con usuario online. "
                + "Controles nuevos. Aviso legal.";
    }

    private void assertStatus(String expected, String a, String da,
                              String b, String db, double similarity) {
        var decision = matcher.compare(publication(a, da), publication(b, db), similarity);
        assertEquals(expected, decision.status(), decision.reason());
    }

    private Publication publication(String title, String description) {
        Publication p = mock(Publication.class);
        when(p.getTitle()).thenReturn(title);
        when(p.getDescription()).thenReturn(description);
        return p;
    }
}
