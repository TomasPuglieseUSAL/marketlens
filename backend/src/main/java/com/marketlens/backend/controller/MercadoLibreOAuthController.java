package com.marketlens.backend.controller;

import com.marketlens.backend.service.MercadoLibreOAuthService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/integrations/mercadolibre")
public class MercadoLibreOAuthController {

    private final MercadoLibreOAuthService oauthService;

    public MercadoLibreOAuthController(
            MercadoLibreOAuthService oauthService) {

        this.oauthService = oauthService;
    }

    @PostMapping("/authorize")
    public Map<String, String> authorize(
            Authentication authentication) {

        String authorizationUrl =
                oauthService.createAuthorizationUrl(
                        authentication.getName()
                );

        return Map.of("authorizationUrl", authorizationUrl);
    }

    @GetMapping(
        value = "/callback",
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {

        if (error != null) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body("La autorización de Mercado Libre fue rechazada.");
        }

        oauthService.completeAuthorization(code, state);

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                    "Mercado Libre conectado correctamente con MarketLens. "
                    + "Ya podés cerrar esta pestaña."
                );
    }

    @GetMapping("/status")
    public OAuthStatus status(Authentication authentication) {

        String username = authentication.getName();

        return new OAuthStatus(
                oauthService.isConnected(username),
                oauthService.getExternalUserId(username),
                oauthService.getExpiresAt(username)
        );
    }

    public record OAuthStatus(
            boolean connected,
            Long externalUserId,
            Instant expiresAt
    ) {
    }
}