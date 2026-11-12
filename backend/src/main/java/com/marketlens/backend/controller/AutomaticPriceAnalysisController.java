package com.marketlens.backend.controller;

import com.marketlens.backend.dto.AutomaticPriceAnalysisResponse;
import com.marketlens.backend.service.AutomaticPriceAnalysisService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publications")
public class AutomaticPriceAnalysisController {

    private final AutomaticPriceAnalysisService service;

    public AutomaticPriceAnalysisController(
            AutomaticPriceAnalysisService service) {
        this.service = service;
    }

    @GetMapping("/{id}/price-analysis")
    public AutomaticPriceAnalysisResponse analyze(
            @PathVariable Long id) {

        return service.analyze(id);
    }
}