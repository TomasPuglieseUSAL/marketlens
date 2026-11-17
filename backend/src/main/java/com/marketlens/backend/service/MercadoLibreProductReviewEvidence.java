package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Las opiniones del PRODUCTO Mercado Libre pueden ser compartidas entre
 * varios vendedores. No equivalen a reputación comercial ni a ventas
 * verificadas. Umbrales exploratorios pendientes de validación empírica.
 */
public final class MercadoLibreProductReviewEvidence {

    public record Signal(String code, String description, int points) { }

    private static final int MAX_AGE_DAYS = 30;
    private static final int MIN_REVIEWS_FOR_FULL_RATING_WEIGHT = 10;
    private static final BigDecimal LOW_RATING_LIMIT = new BigDecimal("3.0");
    private static final BigDecimal MODERATE_RATING_LIMIT = new BigDecimal("4.0");

    private MercadoLibreProductReviewEvidence() { }

    public static boolean hasMetrics(BigDecimal rating, Integer reviews) {
        return rating != null && rating.compareTo(BigDecimal.ZERO) > 0
                && rating.compareTo(new BigDecimal("5.0")) <= 0
                && reviews != null && reviews > 0 && reviews <= 10000000;
    }

    public static boolean hasObservedEvidence(Publication publication, LocalDateTime now) {
        if (publication == null || publication.getMarketplace() != Marketplace.MERCADO_LIBRE
                || !hasMetrics(publication.getMlProductRating(), publication.getMlProductReviewCount())) {
            return false;
        }
        return fresh(publication.getMlProductObservedAt(), now);
    }

    private static boolean fresh(LocalDateTime observedAt, LocalDateTime now) {
        return observedAt != null && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusDays(MAX_AGE_DAYS));
    }

    /**
     * La calificación y el tamaño de muestra son dos señales separadas:
     * una media baja basada en 2 reseñas pesa menos que la basada en 100.
     * No inferir cero reseñas cuando faltan ambas métricas.
     */
    public static List<Signal> evaluateSignals(Publication publication, LocalDateTime now) {
        // Solo la ausencia observada y vigente genera +2 puntos. Nunca
        // convertir una extracción fallida o un dato null en penalización.
        if (publication != null && publication.getMarketplace() == Marketplace.MERCADO_LIBRE
                && Boolean.TRUE.equals(publication.getMlProductRatingUnavailable())
                && !hasMetrics(publication.getMlProductRating(), publication.getMlProductReviewCount())
                && fresh(publication.getMlProductObservedAt(), now)) {
            return List.of(new Signal("ML_PRODUCT_RATING_UNAVAILABLE",
                    "No se mostró una calificación pública del producto en la última observación.",
                    2));
        }
        if (!hasObservedEvidence(publication, now)) return List.of();

        BigDecimal rating = publication.getMlProductRating();
        int reviews = publication.getMlProductReviewCount();
        boolean sufficientSample = reviews >= MIN_REVIEWS_FOR_FULL_RATING_WEIGHT;
        // Descripciones breves y específicas por factor: no repetir la
        // calificación y el recuento en ambas señales.
        String ratingText = rating.setScale(1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + "/5";

        List<Signal> signals = new ArrayList<>(2);
        if (rating.compareTo(LOW_RATING_LIMIT) < 0) {
            signals.add(new Signal("ML_LOW_PRODUCT_RATING",
                    "Calificación baja del producto: " + ratingText + ".",
                    sufficientSample ? 15 : 5));
        } else if (rating.compareTo(MODERATE_RATING_LIMIT) < 0) {
            signals.add(new Signal("ML_MODERATE_PRODUCT_RATING",
                    "Calificación intermedia del producto: " + ratingText + ".",
                    sufficientSample ? 6 : 3));
        } else {
            signals.add(new Signal("ML_PRODUCT_REVIEWS_OBSERVED",
                    "Calificación general del producto: " + ratingText + ". No es una calificación de este vendedor ni verifica esta oferta.", 0));
        }

        // La cantidad limitada de opiniones es una señal independiente
        // de la media de estrellas, sin repetirla en esta descripción.
        if (reviews < MIN_REVIEWS_FOR_FULL_RATING_WEIGHT) {
            int points = reviews <= 4 ? 4 : 2;
            signals.add(new Signal("ML_FEW_PRODUCT_REVIEWS",
                    "Cantidad de opiniones reducida: " + reviews + ".",
                    points));
        }
        return List.copyOf(signals);
    }

    /** Compatibilidad con consumidores que solo necesitan la señal de rating. */
    public static Signal evaluate(Publication publication, LocalDateTime now) {
        List<Signal> signals = evaluateSignals(publication, now);
        return signals.isEmpty() ? null : signals.get(0);
    }
}
