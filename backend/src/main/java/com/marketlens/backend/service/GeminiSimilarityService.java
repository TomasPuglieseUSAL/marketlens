package com.marketlens.backend.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GeminiSimilarityService {

    private final GeminiApiService geminiApiService;

    public GeminiSimilarityService(
            GeminiApiService geminiApiService) {

        this.geminiApiService = geminiApiService;
    }

    public SimilarityResult compare(
            String first,
            String second,
            String third) {

        List<Double> firstVector =
                geminiApiService.embed(first);

        List<Double> secondVector =
                geminiApiService.embed(second);

        List<Double> thirdVector =
                geminiApiService.embed(third);

        return new SimilarityResult(
                cosine(firstVector, secondVector),
                cosine(firstVector, thirdVector),
                firstVector.size()
        );
    }

    private double cosine(
            List<Double> a,
            List<Double> b) {

        if (a.size() != b.size() || a.isEmpty()) {
            throw new IllegalArgumentException(
                    "Los vectores no tienen dimensiones compatibles.");
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);

            dot += x * y;
            normA += x * x;
            normB += y * y;
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (
                Math.sqrt(normA) * Math.sqrt(normB)
        );
    }

    public record SimilarityResult(
            double firstSecondSimilarity,
            double firstThirdSimilarity,
            int dimensions) {}
}