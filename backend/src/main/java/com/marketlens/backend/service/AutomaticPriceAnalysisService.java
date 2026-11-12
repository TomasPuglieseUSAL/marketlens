package com.marketlens.backend.service;

import com.marketlens.backend.dto.AutomaticPriceAnalysisResponse;
import com.marketlens.backend.dto.AutomaticPriceCandidateResponse;
import com.marketlens.backend.dto.PriceCandidateResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.repository.PublicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AutomaticPriceAnalysisService {

    private static final int MAX_SEARCH_RESULTS = 500;
    private static final int MIN_PERCENTILE_REFERENCES = 10;
    private static final int MIN_QUARTILE_REFERENCES = 4;

    private final PublicationRepository publicationRepository;
    private final PriceComparisonService priceComparisonService;
    private final GenericProductMatchingService matchingService;

    public AutomaticPriceAnalysisService(
            PublicationRepository publicationRepository,
            PriceComparisonService priceComparisonService,
            GenericProductMatchingService matchingService) {

        this.publicationRepository = publicationRepository;
        this.priceComparisonService = priceComparisonService;
        this.matchingService = matchingService;
    }

    @Transactional(readOnly = true)
    public AutomaticPriceAnalysisResponse analyze(Long publicationId) {

        Publication target = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Publicación no encontrada."
                ));

        if (target.getPrice() == null ||
                target.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La publicación debe tener un precio positivo."
            );
        }

        // 1. Recuperar candidatos mediante TF-IDF.
        List<PriceCandidateResponse> candidates =
                priceComparisonService.findCandidates(
                        publicationId,
                        MAX_SEARCH_RESULTS
                );

        List<Long> candidateIds = candidates.stream()
                .map(PriceCandidateResponse::publicationId)
                .toList();

        Map<Long, Publication> publications =
                publicationRepository.findAllById(candidateIds)
                        .stream()
                        .collect(Collectors.toMap(
                                Publication::getId,
                                Function.identity()
                        ));

        List<AutomaticPriceCandidateResponse> results =
                new ArrayList<>();

        List<BigDecimal> referencePrices = new ArrayList<>();
        List<Long> referenceIds = new ArrayList<>();
        Set<String> usedReferenceKeys = new HashSet<>();
        String targetReferenceKey = referenceKey(target);

        // 2. Evaluar cada candidato.
        for (PriceCandidateResponse candidate : candidates) {

            Publication publication = publications.get(
                    candidate.publicationId()
            );

            if (publication == null) {
                continue;
            }

            String status;
            String reason;

            if ("OTHER".equals(candidate.marketplace())) {

                status = "INSUFFICIENT_DATA";
                reason = "Origen OTHER excluido de las "
                        + "referencias automáticas.";

            } else {

                var decision = matchingService.compare(
                        target,
                        publication,
                        candidate.similarity()
                );

                status = decision.status();
                reason = decision.reason();
            }

            // La misma oferta puede haberse importado más de una vez.
            // No deduplicar por título: distintos vendedores pueden ofrecer
            // legítimamente productos con títulos iguales.
            String candidateReferenceKey = referenceKey(publication);
            boolean isValidReference = "PROVISIONAL_MATCH".equals(status)
                    && candidate.price() != null
                    && candidate.price().compareTo(BigDecimal.ZERO) > 0;

            if (isValidReference && candidateReferenceKey.equals(targetReferenceKey)) {
                status = "INSUFFICIENT_DATA";
                reason = "Esta referencia identifica la misma oferta que la publicación analizada.";
                isValidReference = false;
            } else if (isValidReference && !usedReferenceKeys.add(candidateReferenceKey)) {
                status = "INSUFFICIENT_DATA";
                reason = "La misma oferta externa ya fue utilizada como referencia.";
                isValidReference = false;
            }

            results.add(new AutomaticPriceCandidateResponse(
                    candidate.publicationId(),
                    candidate.title(),
                    candidate.price(),
                    candidate.similarity(),
                    status,
                    reason
            ));

            if (isValidReference) {

                referencePrices.add(candidate.price());
                referenceIds.add(candidate.publicationId());
            }
        }

        // 3. No se encontraron referencias provisionales.
        if (referencePrices.isEmpty()) {

            return new AutomaticPriceAnalysisResponse(
                    target.getId(),
                    target.getPrice(),
                    "NO_PROVISIONAL_REFERENCES",
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    results,
                    "No se encontraron referencias provisionales "
                    + "suficientemente similares."
            );
        }

        // 4. Ordenar precios y calcular mediana.
        referencePrices.sort(Comparator.naturalOrder());

        int referenceCount = referencePrices.size();

        BigDecimal median = calculateMedian(referencePrices);

        BigDecimal difference = target.getPrice()
                .subtract(median);

        BigDecimal differencePercent = difference
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        median,
                        2,
                        RoundingMode.HALF_UP
                );

        // 5. Calcular cuartiles si hay al menos 4 referencias.
        BigDecimal q1 = null;
        BigDecimal q3 = null;
        BigDecimal iqr = null;

        if (referenceCount >= MIN_QUARTILE_REFERENCES) {

            q1 = calculateQuartile(referencePrices, true);
            q3 = calculateQuartile(referencePrices, false);

            iqr = q3.subtract(q1);
        }

        // 6. Calcular percentil si hay al menos 10 referencias.
        Double percentile = null;

        if (referenceCount >= MIN_PERCENTILE_REFERENCES) {

            percentile = calculatePercentileRank(
                    target.getPrice(),
                    referencePrices
            );
        }

        // 7. Determinar el estado del análisis.
        String analysisStatus;
        String warning;

        if (referenceCount == 1) {

            analysisStatus = "SINGLE_PROVISIONAL_REFERENCE";

            warning = "Comparación descriptiva con una referencia "
                    + "provisional. No se calculan percentiles.";

        } else if (referenceCount < MIN_PERCENTILE_REFERENCES) {

            analysisStatus = "EXPLORATORY_PROVISIONAL";

            warning = "Medidas descriptivas calculadas sobre "
                    + "referencias provisionales. La muestra "
                    + "no alcanza el mínimo experimental "
                    + "para calcular percentiles.";

        } else {

            analysisStatus = "EXPERIMENTAL_PERCENTILE_PROVISIONAL";

            warning = "Percentil experimental calculado sobre "
                    + "referencias seleccionadas automáticamente. "
                    + "La equivalencia comercial no está verificada "
                    + "y la muestra puede no representar al mercado.";
        }

        // 8. Penalización solo si la muestra tiene suficientes ofertas distintas.
        Integer riskPoints = PriceRiskScoring.score(
                target.getPrice(), median, referenceCount);

        // 9. Construir respuesta.
        return new AutomaticPriceAnalysisResponse(
                target.getId(),
                target.getPrice(),
                analysisStatus,
                referenceCount,

                median,
                referenceCount == 1 ? null : median,
                difference,
                differencePercent,

                percentile,
                q1,
                q3,
                iqr,

                riskPoints,
                List.copyOf(referenceIds),
                results,
                warning
        );
    }

    /** Identidad de oferta, no de modelo de producto. */
    private String referenceKey(Publication publication) {
        String marketplace = publication.getMarketplace() == null
                ? "UNKNOWN" : publication.getMarketplace().name();
        String externalId = publication.getExternalId();
        if (externalId != null && !externalId.isBlank()) {
            return marketplace + "|EXTERNAL|" + externalId.strip().toUpperCase(Locale.ROOT);
        }
        // Sin referencia externa, solo podemos reconocer registros repetidos
        // de la misma publicación interna. No deduplicamos títulos.
        return marketplace + "|INTERNAL|" + publication.getId();
    }

    private BigDecimal calculateMedian(
            List<BigDecimal> sortedPrices) {

        int size = sortedPrices.size();

        if (size % 2 != 0) {
            return sortedPrices.get(size / 2);
        }

        return sortedPrices.get(size / 2 - 1)
                .add(sortedPrices.get(size / 2))
                .divide(
                        BigDecimal.valueOf(2),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private BigDecimal calculateQuartile(
            List<BigDecimal> sortedPrices,
            boolean lower) {

        int size = sortedPrices.size();
        int middle = size / 2;

        // Convención: mediana de cada mitad.
        // Si n es impar, se excluye la mediana central.
        List<BigDecimal> half;

        if (lower) {
            half = sortedPrices.subList(0, middle);
        } else {
            int start = (size % 2 == 0)
                    ? middle
                    : middle + 1;

            half = sortedPrices.subList(start, size);
        }

        return calculateMedian(half);
    }

    private Double calculatePercentileRank(
            BigDecimal targetPrice,
            List<BigDecimal> referencePrices) {

        long below = referencePrices.stream()
                .filter(price ->
                        price.compareTo(targetPrice) < 0)
                .count();

        long equal = referencePrices.stream()
                .filter(price ->
                        price.compareTo(targetPrice) == 0)
                .count();

        // Percentil empírico con ajuste por empates:
        //
        // 100 * (cantidad_menores + 0.5 * cantidad_iguales) / N
        //
        // El precio objetivo no está incluido entre
        // las referencias.

        return BigDecimal.valueOf(2 * below + equal)
                .multiply(BigDecimal.valueOf(50))
                .divide(
                        BigDecimal.valueOf(referencePrices.size()),
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }
}