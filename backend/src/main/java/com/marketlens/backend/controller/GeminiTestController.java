package com.marketlens.backend.controller;

import com.marketlens.backend.service.GeminiApiService;
import com.marketlens.backend.service.GeminiSimilarityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/tests")
public class GeminiTestController {

    private final GeminiApiService geminiApiService;
    private final GeminiSimilarityService similarityService;

    public GeminiTestController(
            GeminiApiService geminiApiService,
            GeminiSimilarityService similarityService) {

        this.geminiApiService = geminiApiService;
        this.similarityService = similarityService;
    }

    @PostMapping("/similarity")
    public GeminiSimilarityService.SimilarityResult testSimilarity(
            @RequestBody SimilarityRequest request) {

        if (request.first() == null
                || request.second() == null
                || request.third() == null
                || request.first().isBlank()
                || request.second().isBlank()
                || request.third().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los tres textos son obligatorios.");
        }

        if (request.first().length() > 500
                || request.second().length() > 500
                || request.third().length() > 500) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cada texto admite hasta 500 caracteres.");
        }

        return similarityService.compare(
                request.first(),
                request.second(),
                request.third()
        );
    }

    @PostMapping("/extract-features")
    public Map<String, String> testFeatures(
            @RequestBody FeaturesRequest request) {

        if (request.title() == null
                || request.title().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El título es obligatorio.");
        }

        if (request.title().length() > 300
                || (request.description() != null
                && request.description().length() > 2000)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El texto supera la longitud permitida.");
        }

        String json = geminiApiService.extractFeatures(
                request.title(),
                request.description()
        );

        return Map.of("extractedJson", json);
    }

    public record SimilarityRequest(
            String first,
            String second,
            String third) {}

    public record FeaturesRequest(
            String title,
            String description) {}
}