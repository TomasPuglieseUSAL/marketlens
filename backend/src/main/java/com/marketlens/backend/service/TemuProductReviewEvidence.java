package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Opiniones visibles sobre el PRODUCTO, no sobre el vendedor.
 * Las reseñas no demuestran ventas, identidad del vendedor ni autenticidad.
 * Umbrales exploratorios sujetos a validación.
 */
public final class TemuProductReviewEvidence {
    private static final int MAX_AGE_DAYS = 30;
    private static final int MIN_REVIEWS_FOR_SCORING = 10;

    public record Signal(String code, String description, int points) { }

    private TemuProductReviewEvidence() { }

    public static boolean hasMetrics(BigDecimal rating, Integer reviews) {
        return rating != null
                && rating.compareTo(BigDecimal.ZERO) > 0
                && rating.compareTo(new BigDecimal("5.0")) <= 0
                && reviews != null && reviews > 0 && reviews <= 10000000;
    }

    public static boolean hasObservedEvidence(Publication publication, LocalDateTime now) {
        if (publication == null || publication.getMarketplace() != Marketplace.TEMU) return false;
        LocalDateTime observedAt = publication.getTemuProductObservedAt();
        return observedAt != null && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusDays(MAX_AGE_DAYS))
                && hasMetrics(publication.getTemuProductRating(),
                        publication.getTemuProductReviewCount());
    }

    public static Signal evaluate(Publication publication, LocalDateTime now) {
        if (!hasObservedEvidence(publication, now)) return null;

        BigDecimal rating = publication.getTemuProductRating();
        int reviews = publication.getTemuProductReviewCount();
        String context = "El producto muestra " + rating.toPlainString() + "/5 con "
                + reviews + " opiniones en Temu. Son valoraciones observadas, "
                + "no ventas verificadas ni garantía de autenticidad.";

        // No penalizar por pocas reseñas: insuficiencia no significa fraude.
        if (reviews >= MIN_REVIEWS_FOR_SCORING) {
            if (rating.compareTo(new BigDecimal("3.0")) < 0)
                return new Signal("TEMU_LOW_PRODUCT_RATING", context, 15);
            if (rating.compareTo(new BigDecimal("4.0")) < 0)
                return new Signal("TEMU_MODERATE_PRODUCT_RATING", context, 5);
        }
        return new Signal("TEMU_PRODUCT_REVIEWS_OBSERVED", context, 0);
    }
}
