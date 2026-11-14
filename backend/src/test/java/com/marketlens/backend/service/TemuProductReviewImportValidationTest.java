package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemuProductReviewImportValidationTest {
    private ExtensionPreviewRequest request(String marketplace, String rating, Integer count) {
        return new ExtensionPreviewRequest(
                marketplace, "Funda", "9507", "ARS", "Tokki Shop",
                "606892013277333", "Funda protectora",
                "https://www.temu.com/goods.html?goods_id=606892013277333",
                null, null, null, null,
                rating == null ? null : new BigDecimal(rating), count,
                null, null, null, null, null, null);
    }

    @Test void acceptsValidProductReviewsWithoutStoreReviews() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        when(repo.save(any(ExtensionImport.class))).thenAnswer(call -> call.getArgument(0));
        new ExtensionImportService(repo).importPublication(request("TEMU", "4.7", 286), "demo");
        verify(repo).save(argThat(imported ->
                new BigDecimal("4.7").equals(imported.getTemuProductRating())
                    && imported.getTemuProductReviewCount() == 286));
    }

    @Test void rejectsInvalidProductRatingAndReviewCount() {
        ExtensionImportService service = new ExtensionImportService(null);
        for (String invalid : new String[] {"0", "5.1", "-1", "4.55"}) {
            var e = assertThrows(ResponseStatusException.class,
                    () -> service.importPublication(request("TEMU", invalid, 286), "demo"));
            assertEquals(400, e.getStatusCode().value());
        }
        for (int invalid : new int[] {-1, 10000001}) {
            var e = assertThrows(ResponseStatusException.class,
                    () -> service.importPublication(request("TEMU", "4.7", invalid), "demo"));
            assertEquals(400, e.getStatusCode().value());
        }
    }

    @Test void rejectsTemuReviewEvidenceOnOtherMarketplaces() {
        var e = assertThrows(ResponseStatusException.class,
                () -> new ExtensionImportService(null).importPublication(
                        request("MERCADO_LIBRE", "4.7", 286), "demo"));
        assertEquals(400, e.getStatusCode().value());
    }
}
