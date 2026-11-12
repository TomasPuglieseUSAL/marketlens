package com.marketlens.backend.controller;

import com.marketlens.backend.dto.CreateSellerRequest;
import com.marketlens.backend.dto.SellerResponse;
import com.marketlens.backend.service.SellerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.marketlens.backend.dto.SellerListItemResponse;

@RestController
@RequestMapping("/api/sellers")
public class SellerController {

    private final SellerService sellerService;

    public SellerController(SellerService sellerService) {
        this.sellerService = sellerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SellerResponse create(
            @Valid @RequestBody CreateSellerRequest request) {

        return sellerService.create(request);
    }

    @GetMapping("/{id}")
    public SellerResponse getById(
            @PathVariable Long id) {

        return sellerService.getById(id);
    }

    @GetMapping
    public Page<SellerListItemResponse> getAll(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size) {

    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 50));

    Pageable pageable = PageRequest.of(
        safePage,
        safeSize,
        Sort.by(Sort.Direction.DESC, "id")
    );

    return sellerService.getAll(pageable);
    }
}