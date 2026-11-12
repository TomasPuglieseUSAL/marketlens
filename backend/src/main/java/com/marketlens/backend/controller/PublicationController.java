package com.marketlens.backend.controller;

import java.util.List;

import com.marketlens.backend.dto.CreatePublicationRequest;
import com.marketlens.backend.dto.PublicationResponse;
import com.marketlens.backend.service.PublicationService;
import com.marketlens.backend.service.RiskEvaluationService;
import com.marketlens.backend.dto.RiskEvaluationResponse;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.marketlens.backend.dto.PublicationListItemResponse;



@RestController
@RequestMapping("/api/publications")
public class PublicationController {

    private final PublicationService publicationService;
    private final RiskEvaluationService riskEvaluationService;

    public PublicationController(
        PublicationService publicationService,
        RiskEvaluationService riskEvaluationService) {

    this.publicationService = publicationService;
    this.riskEvaluationService = riskEvaluationService;
}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PublicationResponse create(@Valid @RequestBody CreatePublicationRequest request) {
        return publicationService.create(request);
    }

    @PostMapping("/{id}/evaluations")
    @ResponseStatus(HttpStatus.CREATED)
    public RiskEvaluationResponse evaluate(@PathVariable Long id) {
        return riskEvaluationService.evaluate(id);
    }

    @GetMapping("/{id}")
    public PublicationResponse getById(
        @PathVariable Long id) {

        return publicationService.getById(id);
    }

    @GetMapping("/{id}/evaluations")
    public List<RiskEvaluationResponse> getEvaluations(
        @PathVariable Long id) {

            return riskEvaluationService.getByPublication(id);
    }

    @GetMapping
    public Page<PublicationListItemResponse> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {

    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 50));

    Pageable pageable = PageRequest.of(
        safePage,
        safeSize,
        Sort.by(Sort.Direction.DESC, "id")
    );

    return publicationService.getAll(pageable);
    }
}