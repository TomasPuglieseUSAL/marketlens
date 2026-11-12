package com.marketlens.backend.controller;

import com.marketlens.backend.dto.PriceCandidateResponse;
import com.marketlens.backend.service.PriceComparisonService;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/publications")
public class PriceComparisonController {

    private final PriceComparisonService priceComparisonService;

    public PriceComparisonController(
            PriceComparisonService priceComparisonService) {

        this.priceComparisonService = priceComparisonService;
    }

    @GetMapping("/{id}/price-candidates")
    public List<PriceCandidateResponse> getPriceCandidates(
            @PathVariable Long id) {

        return priceComparisonService.findCandidates(id);
    }
}