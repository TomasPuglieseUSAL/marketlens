package com.marketlens.backend.service;

import com.marketlens.backend.dto.PriceCandidateResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class PriceComparisonService {

    private static final int MAX_SEARCH_RESULTS = 500;
    private static final int DEFAULT_MAX_CANDIDATES = 20;
    private static final double MIN_TEXT_SIMILARITY = 0.05;

    private final PublicationRepository publicationRepository;
    private final PublicationSimilarityService similarityService;

    public PriceComparisonService(
            PublicationRepository publicationRepository,
            PublicationSimilarityService similarityService) {

        this.publicationRepository = publicationRepository;
        this.similarityService = similarityService;
    }

    @Transactional(readOnly = true)
    public List<PriceCandidateResponse> findCandidates(
            Long publicationId) {

        return findCandidates(
                publicationId,
                DEFAULT_MAX_CANDIDATES
        );
    }

    @Transactional(readOnly = true)
    public List<PriceCandidateResponse> findCandidates(
            Long publicationId,
            int maxCandidates) {

        if (maxCandidates < 1 ||
                maxCandidates > MAX_SEARCH_RESULTS) {

            throw new IllegalArgumentException(
                    "maxCandidates debe estar entre 1 y "
                    + MAX_SEARCH_RESULTS
            );
        }

        Publication target = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId
                ));

        List<Publication> publications = publicationRepository
                .findByIdNotAndPriceGreaterThanOrderByCreatedAtDesc(
                        publicationId,
                        BigDecimal.ZERO,
                        PageRequest.of(0, MAX_SEARCH_RESULTS)
                );

        Map<Long, Double> similarities =
                similarityService.calculateSimilarities(
                        target,
                        publications
                );

        return publications.stream()
                .map(candidate -> {

                    double similarity = similarities.getOrDefault(
                            candidate.getId(),
                            0.0
                    );

                    return new PriceCandidateResponse(
                            candidate.getId(),
                            candidate.getTitle(),
                            candidate.getMarketplace().name(),
                            candidate.getPrice(),
                            similarity,
                            "PENDING_REVIEW"
                    );
                })
                .filter(candidate ->
                        candidate.similarity() >= MIN_TEXT_SIMILARITY)
                .sorted(
                        Comparator.comparingDouble(
                                PriceCandidateResponse::similarity
                        ).reversed()
                )
                .limit(maxCandidates)
                .toList();
    }
}