package com.marketlens.backend.controller;

import com.marketlens.backend.dto.SellerSalesObservationRequest;
import com.marketlens.backend.dto.SellerSalesObservationResponse;
import com.marketlens.backend.service.SellerSalesObservationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/publications")
public class SellerSalesObservationController {

    private final SellerSalesObservationService service;

    public SellerSalesObservationController(SellerSalesObservationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/seller-observation")
    public SellerSalesObservationResponse observe(
            @PathVariable Long id,
            @RequestBody SellerSalesObservationRequest request,
            Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication.getName() == null ||
                "anonymousUser".equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Se necesita una sesión de MarketLens.");
        }
        return service.observe(id, request);
    }
}
