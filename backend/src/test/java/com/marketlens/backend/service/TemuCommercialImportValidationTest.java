package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TemuCommercialImportValidationTest {

    private ExtensionPreviewRequest request(String marketplace, String seller,
                                            String tenure, String productSold) {
        return new ExtensionPreviewRequest(
                marketplace, "Funda", "9507", "ARS", seller,
                "606892013277333", "Funda protectora",
                "https://www.temu.com/goods.html?goods_id=606892013277333",
                null, null, null, null, null, null, null, null,
                null, null, null, null, tenure, productSold);
    }

    @Test void storesRawApproximateSignalsFromTemuWithoutChangingThem() {
        ExtensionImportRepository repo = mock(ExtensionImportRepository.class);
        when(repo.save(any(ExtensionImport.class))).thenAnswer(invocation -> invocation.getArgument(0));
        new ExtensionImportService(repo).importPublication(
                request("TEMU", "Tokki Shop", "1 year on Temu", "15K+ sold"), "demo");

        verify(repo).save(argThat(row ->
                "1 year on Temu".equals(row.getTemuStoreTenureText())
                && "15K+ sold".equals(row.getTemuProductSoldText())));
    }

    @Test void rejectsUnsupportedOrAmbiguousNumbers() {
        ExtensionImportService service = new ExtensionImportService(null);
        for (String invalidAge : new String[] {"recent", "0 weeks on Temu",
                "1000 years on Temu", "Yesterday"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.importPublication(
                            request("TEMU", "Tokki Shop", invalidAge, null), "demo"))
                    .getStatusCode().value());
        }
        for (String invalidSales : new String[] {"some sold", "1.2K+ shop sold",
                "3 sales", "0.5 sold", "300M+ sold"}) {
            assertEquals(400, assertThrows(ResponseStatusException.class,
                    () -> service.importPublication(
                            request("TEMU", "Tokki Shop", null, invalidSales), "demo"))
                    .getStatusCode().value());
        }
    }

    @Test void rejectsCrossMarketplaceFieldsAndUnknownSellerAge() {
        ExtensionImportService service = new ExtensionImportService(null);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.importPublication(
                        request("MERCADO_LIBRE", "Seller", "1 week on Temu", null), "demo"))
                .getStatusCode().value());
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.importPublication(
                        request("TEMU", null, "1 week on Temu", null), "demo"))
                .getStatusCode().value());
    }
}
