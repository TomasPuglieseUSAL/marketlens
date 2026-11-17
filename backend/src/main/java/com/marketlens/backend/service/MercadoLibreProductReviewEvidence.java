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
 * verificadas. Se usan únicamente como información contextual.
 */
public final class MercadoLibreProductReviewEvidence {

    public record Signal(String code, String description, int points) { }

    private static final int MAX_AGE_DAYS = 30;
    private static final int MIN_REVIEWS_FOR_SAMPLE_CONTEXT = 10;
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
     * Las opiniones del MODELO pueden corresponder a otros vendedores.
     * Son información contextual, nunca evidencia de que esta publicación
     * o la transacción individual sea confiable o fraudulenta.
     * Su puntuación NO modifica el riesgo de la oferta.
     */
    public static List<Signal> evaluateSignals(Publication publication, LocalDateTime now) {
        if (publication != null && publication.getMarketplace() == Marketplace.MERCADO_LIBRE
                && Boolean.TRUE.equals(publication.getMlProductRatingUnavailable())
                && !hasMetrics(publication.getMlProductRating(), publication.getMlProductReviewCount())
                && fresh(publication.getMlProductObservedAt(), now)) {
            return List.of(new Signal("ML_PRODUCT_RATING_UNAVAILABLE",
                    "No se encontró una calificación general del producto en la última observación. "
                    + "Su ausencia no acredita ni desacredita esta oferta.", 0));
        }
        if (!hasObservedEvidence(publication, now)) return List.of();

        BigDecimal rating = publication.getMlProductRating();
        int reviews = publication.getMlProductReviewCount();
        String ratingText = rating.setScale(1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',') + "/5";

        List<Signal> signals = new ArrayList<>(2);
        if (rating.compareTo(LOW_RATING_LIMIT) < 0) {
            signals.add(new Signal("ML_LOW_PRODUCT_RATING",
                    "Calificación general baja del producto: " + ratingText
                            + ". No evalúa al vendedor ni acredita esta oferta.", 0));
        } else if (rating.compareTo(MODERATE_RATING_LIMIT) < 0) {
            signals.add(new Signal("ML_MODERATE_PRODUCT_RATING",
                    "Calificación general intermedia del producto: " + ratingText
                            + ". No evalúa al vendedor ni acredita esta oferta.", 0));
        } else {
            signals.add(new Signal("ML_PRODUCT_REVIEWS_OBSERVED",
                    "Calificación general del producto: " + ratingText
                            + ". No es una calificación de este vendedor ni verifica esta oferta.", 0));
        }

        if (reviews < MIN_REVIEWS_FOR_FULL_RATING_WEIGHT) {
            signals.add(new Signal("ML_FEW_PRODUCT_REVIEWS",
                    "Muestra reducida del producto: " + reviews
                            + " opiniones. Es información general del modelo, no de este vendedor.", 0));
        }
        return List.copyOf(signals);
    }

    /** Compatibilidad con consumidores que solo necesitan la señal de rating. */
    public static Signal evaluate(Publication publication, LocalDateTime now) {
        List<Signal> signals = evaluateSignals(publication, now);
        return signals.isEmpty() ? null : signals.get(0);
    }
}
