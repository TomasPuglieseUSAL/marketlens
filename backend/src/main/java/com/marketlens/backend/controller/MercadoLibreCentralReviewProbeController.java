package com.marketlens.backend.controller;

import com.marketlens.backend.service.MercadoLibreApiService;
import com.marketlens.backend.service.MercadoLibreItemReviewEvidence;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Diagnóstico acotado de OAuth centralizado.
 * Solo el operador configurado puede disparar consultas manuales.
 * Nunca expone access tokens ni contenido privado de las opiniones.
 */
@RestController
@RequestMapping("/api/integrations/mercadolibre/central-reviews")
public class MercadoLibreCentralReviewProbeController {
    private final MercadoLibreApiService api;

    public MercadoLibreCentralReviewProbeController(MercadoLibreApiService api) {
        this.api = api;
    }

    @GetMapping("/status")
    public Status status(Authentication authentication) {
        String requester = requireOperator(authentication);
        return new Status(api.hasReviewAccess(requester));
    }

    @GetMapping("/probe/{itemId}")
    public Probe probe(@PathVariable String itemId, Authentication authentication) {
        String requester = requireOperator(authentication);
        if (!api.hasReviewAccess(requester)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La cuenta de integración todavía no tiene una autorización OAuth vigente.");
        }

        MercadoLibreItemReviewEvidence.Verified verified =
                api.getVerifiedItemReviews(requester, itemId);
        if (verified == null) {
            return new Probe(itemId, "NOT_ITEM_VERIFIED", null, null);
        }
        return new Probe(verified.itemId(), "ITEM_VERIFIED",
                verified.rating().toPlainString(), verified.count());
    }

    private String requireOperator(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !api.isCentralReviewsOwner(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Operación reservada al usuario configurado para la integración.");
        }
        return authentication.getName();
    }

    public record Status(boolean connected) { }

    public record Probe(String itemId, String result, String rating, Integer reviewCount) { }
}
