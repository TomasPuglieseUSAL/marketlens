package com.marketlens.backend.service;

import com.marketlens.backend.entity.Publication;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PublicationSimilarityService {

    private static final double WORD_WEIGHT = 0.60;
    private static final double CHAR_WEIGHT = 0.40;
    private static final int NGRAM_SIZE = 3;

    private static final Set<String> STOP_WORDS = Set.of(
            "de", "del", "la", "el", "los", "las",
            "un", "una", "con", "sin", "para", "por",
            "en", "y", "vendo", "venta", "urgente",
            "oferta", "excelente", "color", "nuevo",
            "nueva", "usado", "usada"
    );

    public Map<Long, Double> calculateSimilarities(
            Publication target,
            List<Publication> candidates) {

        List<Publication> documents = new ArrayList<>();
        documents.add(target);
        documents.addAll(candidates);

        // Características de palabras y caracteres.
        List<Map<String, Integer>> wordFrequencies =
                new ArrayList<>();

        List<Map<String, Integer>> charFrequencies =
                new ArrayList<>();

        for (Publication publication : documents) {

            List<String> words = tokenize(
                    publication.getTitle()
            );

            wordFrequencies.add(
                    frequencies(words)
            );

            charFrequencies.add(
                    frequencies(characterNgrams(words))
            );
        }

        Map<String, Integer> wordDf =
                documentFrequencies(wordFrequencies);

        Map<String, Integer> charDf =
                documentFrequencies(charFrequencies);

        int documentCount = documents.size();

        Map<String, Double> targetWordVector =
                toTfidf(
                        wordFrequencies.get(0),
                        wordDf,
                        documentCount
                );

        Map<String, Double> targetCharVector =
                toTfidf(
                        charFrequencies.get(0),
                        charDf,
                        documentCount
                );

        Map<Long, Double> results = new HashMap<>();

        for (int i = 0; i < candidates.size(); i++) {

            Map<String, Double> wordVector =
                    toTfidf(
                            wordFrequencies.get(i + 1),
                            wordDf,
                            documentCount
                    );

            Map<String, Double> charVector =
                    toTfidf(
                            charFrequencies.get(i + 1),
                            charDf,
                            documentCount
                    );

            double wordSimilarity = cosineSimilarity(
                    targetWordVector,
                    wordVector
            );

            double charSimilarity = cosineSimilarity(
                    targetCharVector,
                    charVector
            );

            double combinedSimilarity =
                    WORD_WEIGHT * wordSimilarity
                    + CHAR_WEIGHT * charSimilarity;

            results.put(
                    candidates.get(i).getId(),
                    combinedSimilarity
            );
        }

        return results;
    }

    public double calculateSimilarity(
            Publication first,
            Publication second) {

        return calculateSimilarities(
                first,
                List.of(second)
        ).getOrDefault(second.getId(), 0.0);
    }

    private List<String> tokenize(String text) {

        String normalized = normalize(text);

        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                    normalized.split("[^\\p{L}\\p{N}]+")
                )
                .filter(token -> !token.isBlank())
                .filter(token -> !STOP_WORDS.contains(token))
                .toList();
    }

    private String normalize(String text) {

        if (text == null) {
            return "";
        }

        String normalized = Normalizer.normalize(
                text.toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        );

        normalized = normalized.replaceAll("\\p{M}+", "");

        // Normalización genérica de unidades.
        normalized = normalized.replaceAll(
                "\\b(\\d+)\\s+(gb|tb)\\b",
                "$1$2"
        );

        return normalized;
    }

    private List<String> characterNgrams(
            List<String> words) {

        // Al eliminar separaciones, "Play Station" y
        // "Playstation" producen secuencias equivalentes.
        String compact = String.join("", words);

        if (compact.length() < NGRAM_SIZE) {
            return List.of();
        }

        List<String> ngrams = new ArrayList<>();

        for (int i = 0;
             i <= compact.length() - NGRAM_SIZE;
             i++) {

            ngrams.add(
                    compact.substring(
                            i,
                            i + NGRAM_SIZE
                    )
            );
        }

        return ngrams;
    }

    private Map<String, Integer> frequencies(
            List<String> terms) {

        Map<String, Integer> result = new HashMap<>();

        for (String term : terms) {
            result.merge(term, 1, Integer::sum);
        }

        return result;
    }

    private Map<String, Integer> documentFrequencies(
            List<Map<String, Integer>> documents) {

        Map<String, Integer> result = new HashMap<>();

        for (Map<String, Integer> document : documents) {

            for (String term : document.keySet()) {
                result.merge(term, 1, Integer::sum);
            }
        }

        return result;
    }

    private Map<String, Double> toTfidf(
            Map<String, Integer> frequencies,
            Map<String, Integer> documentFrequency,
            int documentCount) {

        Map<String, Double> vector = new HashMap<>();

        for (var entry : frequencies.entrySet()) {

            String term = entry.getKey();

            double tf = 1.0 + Math.log(entry.getValue());

            double idf = Math.log(
                    (1.0 + documentCount) /
                    (1.0 + documentFrequency.get(term))
            ) + 1.0;

            vector.put(term, tf * idf);
        }

        return vector;
    }

    private double cosineSimilarity(
            Map<String, Double> first,
            Map<String, Double> second) {

        if (first.isEmpty() || second.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double normFirst = 0.0;
        double normSecond = 0.0;

        for (var entry : first.entrySet()) {

            double value = entry.getValue();

            dotProduct += value
                    * second.getOrDefault(
                            entry.getKey(),
                            0.0
                    );

            normFirst += value * value;
        }

        for (double value : second.values()) {
            normSecond += value * value;
        }

        if (normFirst == 0.0 || normSecond == 0.0) {
            return 0.0;
        }

        return dotProduct / (
                Math.sqrt(normFirst)
                * Math.sqrt(normSecond)
        );
    }
}