package com.marketlens.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MercadoLibreOAuthServiceDemoTest {

    private MercadoLibreOAuthService disabledService() {
        MercadoLibreOAuthService service = new MercadoLibreOAuthService(
                "", "", "http://localhost:8080/callback", true);
        ReflectionTestUtils.setField(service, "enabled", false);
        return service;
    }

    @Test
    void demoBlocksAuthorizationUrlCreation() {
        MercadoLibreOAuthService service = disabledService();
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createAuthorizationUrl("demo"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void demoBlocksCodeExchange() {
        MercadoLibreOAuthService service = disabledService();
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.completeAuthorization("code", "state"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void demoBlocksApiTokenAccess() {
        MercadoLibreOAuthService service = disabledService();
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getAccessToken("demo"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertFalse(service.isConnected("demo"));
        assertNull(service.getExternalUserId("demo"));
        assertNull(service.getExpiresAt("demo"));
    }
}
