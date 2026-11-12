package com.marketlens.backend.controller;

import com.marketlens.backend.dto.MercadoLibreUserResponse;
import com.marketlens.backend.dto.MercadoLibreUserProductResponse;
import com.marketlens.backend.service.MercadoLibreApiService;
import com.marketlens.backend.dto.MercadoLibreSellerLookupResponse;
import com.marketlens.backend.dto.MercadoLibreItemResponse;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/integrations/mercadolibre")
public class MercadoLibreApiController {

    private final MercadoLibreApiService apiService;

    public MercadoLibreApiController(
            MercadoLibreApiService apiService) {

        this.apiService = apiService;
    }

    @GetMapping("/me")
    public MercadoLibreUserResponse getCurrentUser(
            Authentication authentication) {

        return apiService.getCurrentUser(
                authentication.getName());
    }

    @GetMapping("/user-products/{id}")
    public MercadoLibreUserProductResponse getUserProduct(
            @PathVariable String id,
            Authentication authentication) {

        return apiService.getUserProduct(
                authentication.getName(),
                id);
    }

    @GetMapping("/sellers/by-nickname/{nickname}")
    public MercadoLibreSellerLookupResponse findSellerByNickname(
            @PathVariable String nickname,
            Authentication authentication) {

        return apiService.findSellerByNickname(
                authentication.getName(),
                nickname);
    }

    @GetMapping("/users/{id}")
    public MercadoLibreUserResponse getPublicUser(
            @PathVariable Long id,
            Authentication authentication) {

        return apiService.getPublicUser(
                authentication.getName(),
                id);
    }

    @GetMapping("/items/{id}")
    public MercadoLibreItemResponse getItem(
            @PathVariable String id,
            Authentication authentication) {

        return apiService.getItem(
                authentication.getName(),
                id);
    }

    @GetMapping("/items/{id}/seller")
    public MercadoLibreUserResponse getItemSeller(
            @PathVariable String id,
            Authentication authentication) {

        return apiService.getItemSeller(
                authentication.getName(),
                id);
    }
}