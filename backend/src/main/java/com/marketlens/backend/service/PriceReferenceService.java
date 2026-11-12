package com.marketlens.backend.service;

import com.marketlens.backend.dto.PriceReferenceResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.repository.PublicationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
public class PriceReferenceService {

    private final PublicationRepository publicationRepository;

    public PriceReferenceService(
            PublicationRepository publicationRepository) {

        this.publicationRepository = publicationRepository;
    }

    @Transactional(readOnly = true)
    public PriceReferenceResponse analyze(
            Long publicationId,
            List<Long> referenceIds) {

        if (referenceIds == null || referenceIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Se requiere al menos una publicación de referencia.");
        }

        if (referenceIds.stream().anyMatch(Objects::isNull)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Los identificadores no pueden ser nulos.");
        }

        List<Long> ids = referenceIds.stream()
                .distinct()
                .toList();

        if (ids.size() > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Se permiten hasta 100 referencias.");
        }

        if (ids.contains(publicationId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Una publicación no puede compararse consigo misma.");
        }

        Publication target = publicationRepository
                .findById(publicationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Publicación no encontrada."));

        if (target.getPrice() == null ||
                target.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El precio de la publicación debe ser positivo.");
        }

        List<Publication> references =
                publicationRepository.findAllById(ids);

        if (references.size() != ids.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Una o más publicaciones de referencia no existen.");
        }

        for (Publication reference : references) {

            if (reference.getPrice() == null ||
                    reference.getPrice().compareTo(BigDecimal.ZERO) <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Las referencias deben tener precios positivos.");
            }
        }

        // Se presupone que todos estos precios están en ARS.
        // La equivalencia de los productos debe haberse
        // verificado antes de enviar sus identificadores.

        List<BigDecimal> prices = references.stream()
                .map(Publication::getPrice)
                .sorted()
                .toList();

        BigDecimal median = calculateMedian(prices);

        BigDecimal referencePrice = median;

        BigDecimal difference = target.getPrice()
                .subtract(referencePrice);

        BigDecimal percentage = difference
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        referencePrice,
                        2,
                        RoundingMode.HALF_UP
                );

        boolean singleReference = prices.size() == 1;

        return new PriceReferenceResponse(
                target.getId(),
                target.getPrice(),
                ids,
                prices.size(),
                referencePrice,
                singleReference ? null : median,
                difference,
                percentage,
                singleReference
                        ? "SINGLE_REFERENCE"
                        : "EXPLORATORY",
                singleReference
                        ? "Comparación descriptiva con una referencia. "
                          + "No permite estimar percentiles ni riesgo estadístico."
                        : "Análisis exploratorio sobre referencias seleccionadas. "
                          + "Todavía no genera puntos automáticos de riesgo."
        );
    }

    private BigDecimal calculateMedian(
            List<BigDecimal> sortedPrices) {

        int size = sortedPrices.size();

        if (size % 2 == 1) {
            return sortedPrices.get(size / 2);
        }

        BigDecimal a = sortedPrices.get(size / 2 - 1);
        BigDecimal b = sortedPrices.get(size / 2);

        return a.add(b).divide(
                BigDecimal.valueOf(2),
                2,
                RoundingMode.HALF_UP
        );
    }
}