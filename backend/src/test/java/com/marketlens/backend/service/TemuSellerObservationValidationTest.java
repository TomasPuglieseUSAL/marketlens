package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TemuSellerObservationValidationTest {

    private ExtensionPreviewRequest request(String seller, BigDecimal rating) {
        return new ExtensionPreviewRequest(
                "TEMU",
                "Funda para teléfono",
                "9507",
                "ARS",
                seller,
                "606892013277333",
                "Funda protectora",
                "https://www.temu.com/ar-en/funda-g-606892013277333.html",
                null, null, rating, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private ExtensionPreviewRequest withReviewEvidence(
            Integer count, String storeId, Boolean inspected, String profileUrl) {
        return new ExtensionPreviewRequest(
                "TEMU", "Funda para teléfono", "9507", "ARS",
                "Tokki Shop", "606892013277333", "Funda protectora",
                "https://www.temu.com/ar-en/funda-g-606892013277333.html",
                profileUrl, null, new BigDecimal("4.8"),
                count, null, null, storeId, inspected,
                null, null, null, null, null, null, null);
    }

    @Test
    void rejectsNegativeStoreReviews() {
        ExtensionImportService service = new ExtensionImportService(null);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(withReviewEvidence(
                        -1, "613456789012345", true, null), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsStoreReviewsWithoutSellerSectionEvidence() {
        ExtensionImportService service = new ExtensionImportService(null);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(withReviewEvidence(
                        15, "613456789012345", false, null), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsInconsistentOrForeignStoreProfile() {
        ExtensionImportService service = new ExtensionImportService(null);
        var error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(withReviewEvidence(
                        15, "613456789012345", true,
                        "https://other.example/mall.html?mall_id=613456789012345"), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void doesNotAcceptProductRatingAsSellerName() {
        ExtensionImportService service = new ExtensionImportService(null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(request("4.7 out of five stars", null), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsStoreRatingWithoutSellerIdentity() {
        ExtensionImportService service = new ExtensionImportService(null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(request(null, new BigDecimal("4.8")), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsOutOfRangeStoreRating() {
        ExtensionImportService service = new ExtensionImportService(null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.importPublication(request("Tokki Shop", new BigDecimal("5.5")), "demo"));
        assertEquals(400, error.getStatusCode().value());
    }
}
