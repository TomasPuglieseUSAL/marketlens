package com.marketlens.backend.controller;

import com.marketlens.backend.dto.CreateReportRequest;
import com.marketlens.backend.dto.ReportResponse;
import com.marketlens.backend.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
@RequestMapping("/api/publications/{publicationId}/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse create(
        @PathVariable Long publicationId,
        @Valid @RequestBody CreateReportRequest request,
        Authentication authentication) {
            
        return reportService.create(
            publicationId,
            authentication.getName(),
            request
        );
    }

    @GetMapping
    public List<ReportResponse> getByPublication(
            @PathVariable Long publicationId) {

        return reportService.getByPublication(publicationId);
    }
}