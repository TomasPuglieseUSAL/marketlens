package com.marketlens.backend.controller;

import com.marketlens.backend.dto.PriceReferenceRequest;
import com.marketlens.backend.dto.PriceReferenceResponse;
import com.marketlens.backend.service.PriceReferenceService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publications")
public class PriceReferenceController {

    private final PriceReferenceService priceReferenceService;

    public PriceReferenceController(
            PriceReferenceService priceReferenceService) {

        this.priceReferenceService = priceReferenceService;
    }

    @PostMapping("/{id}/price-reference")
    public PriceReferenceResponse analyze(
            @PathVariable Long id,
            @RequestBody PriceReferenceRequest request) {

        return priceReferenceService.analyze(
                id,
                request.referenceIds()
        );
    }
}