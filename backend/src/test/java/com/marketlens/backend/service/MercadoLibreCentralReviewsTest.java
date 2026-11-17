package com.marketlens.backend.service;

import com.marketlens.backend.controller.MercadoLibreCentralReviewProbeController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MercadoLibreCentralReviewsTest {

    private final MercadoLibreOAuthService oauth = mock(MercadoLibreOAuthService.class);
    private final MercadoLibreApiService api = new MercadoLibreApiService(oauth);

    @Test
    void disabledCentralModeKeepsExistingUserScopedBehavior() {
        when(oauth.isConnected("persona")).thenReturn(true);

        assertFalse(api.isCentralReviewsEnabled());
        assertEquals("persona", api.reviewTokenOwner("persona"));
        assertTrue(api.hasReviewAccess("persona"));
        verify(oauth).isConnected("persona");
    }

    @Test
    void centralModeUsesOnlyConfiguredIntegrationAccountForAllUsers() {
        ReflectionTestUtils.setField(api, "centralReviewsEnabled", true);
        ReflectionTestUtils.setField(api, "centralReviewsOwner", "operador");
        when(oauth.isConnected("operador")).thenReturn(true);

        assertTrue(api.isCentralReviewsEnabled());
        assertTrue(api.isCentralReviewsOwner("operador"));
        assertFalse(api.isCentralReviewsOwner("persona"));
        assertEquals("operador", api.reviewTokenOwner("persona"));
        assertEquals("operador", api.reviewTokenOwner("otro_usuario"));
        assertTrue(api.hasReviewAccess("persona"));
        assertTrue(api.hasReviewAccess("otro_usuario"));
        verify(oauth, times(2)).isConnected("operador");
        verify(oauth, never()).isConnected("persona");
        verify(oauth, never()).isConnected("otro_usuario");
    }

    @Test
    void missingCentralOwnerFailsClosedRatherThanUsingPersonalTokens() {
        ReflectionTestUtils.setField(api, "centralReviewsEnabled", true);
        ReflectionTestUtils.setField(api, "centralReviewsOwner", "");

        assertFalse(api.isCentralReviewsEnabled());
        assertNull(api.reviewTokenOwner("persona"));
        assertFalse(api.hasReviewAccess("persona"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> api.getVerifiedItemReviews("persona", "MLA123456789"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verifyNoInteractions(oauth);
    }

    @Test
    void onlyConfiguredOperatorCanRunDiagnosticProbe() {
        MercadoLibreApiService mocked = mock(MercadoLibreApiService.class);
        var controller = new MercadoLibreCentralReviewProbeController(mocked);
        Authentication operator = mock(Authentication.class);
        when(operator.isAuthenticated()).thenReturn(true);
        when(operator.getName()).thenReturn("operador");
        when(mocked.isCentralReviewsOwner("operador")).thenReturn(true);
        when(mocked.hasReviewAccess("operador")).thenReturn(true);
        when(mocked.getVerifiedItemReviews("operador", "MLA123456789"))
                .thenReturn(new MercadoLibreItemReviewEvidence.Verified(
                        "MLA123456789", new BigDecimal("4.8"), 18));

        assertTrue(controller.status(operator).connected());
        var outcome = controller.probe("MLA123456789", operator);
        assertEquals("ITEM_VERIFIED", outcome.result());
        assertEquals("4.8", outcome.rating());
        assertEquals(18, outcome.reviewCount());

        Authentication stranger = mock(Authentication.class);
        when(stranger.isAuthenticated()).thenReturn(true);
        when(stranger.getName()).thenReturn("persona");
        ResponseStatusException blocked = assertThrows(ResponseStatusException.class,
                () -> controller.probe("MLA123456789", stranger));
        assertEquals(HttpStatus.FORBIDDEN, blocked.getStatusCode());
        verify(mocked, never()).getVerifiedItemReviews("persona", "MLA123456789");
    }

    @Test
    void diagnosticDistinguishesMissingReviewsWithoutClaimingApiFailure() {
        MercadoLibreApiService mocked = mock(MercadoLibreApiService.class);
        var controller = new MercadoLibreCentralReviewProbeController(mocked);
        Authentication operator = mock(Authentication.class);
        when(operator.isAuthenticated()).thenReturn(true);
        when(operator.getName()).thenReturn("operador");
        when(mocked.isCentralReviewsOwner("operador")).thenReturn(true);
        when(mocked.hasReviewAccess("operador")).thenReturn(true);

        var result = controller.probe("MLA123456789", operator);
        assertEquals("NOT_ITEM_VERIFIED", result.result());
        assertNull(result.rating());
        assertNull(result.reviewCount());
    }
}
