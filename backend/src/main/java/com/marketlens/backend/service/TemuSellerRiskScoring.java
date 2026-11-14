package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Reglas exploratorias específicas de Temu.
 * Los recuentos corresponden a RESEÑAS de tienda, nunca a ventas.
 * Un dato de navegador sin evidencia de inspección no se interpreta como ausencia.
 */
public final class TemuSellerRiskScoring {

    public record Signal(String code, String description, int points) { }

    private static final int MAX_AGE_DAYS = 30;
    private static final int MIN_REVIEWS_FOR_RATING = 10;

    private TemuSellerRiskScoring() { }

    public static boolean isCurrent(Publication publication, LocalDateTime now) {
        LocalDateTime at = publication.getTemuSellerObservedAt();
        return at != null && !at.isAfter(now)
                && !at.isBefore(now.minusDays(MAX_AGE_DAYS));
    }

    public static boolean hasSupportedReputation(Publication publication, LocalDateTime now) {
        if (publication.getMarketplace() != Marketplace.TEMU ||
                publication.getSeller() == null || !isCurrent(publication, now) ||
                !Boolean.TRUE.equals(publication.getTemuSellerSectionObserved()) ||
                publication.getTemuStoreId() == null) return false;

        BigDecimal rating = publication.getTemuSellerRating();
        Integer reviews = publication.getTemuSellerReviewCount();
        return rating != null && rating.compareTo(BigDecimal.ZERO) > 0
                && rating.compareTo(new BigDecimal("5.0")) <= 0
                && reviews != null && reviews >= MIN_REVIEWS_FOR_RATING;
    }

    public static Signal evaluate(Publication publication, LocalDateTime now) {
        if (publication.getMarketplace() != Marketplace.TEMU ||
                publication.getSeller() == null || !isCurrent(publication, now)) return null;

        BigDecimal rating = publication.getTemuSellerRating();
        Integer reviews = publication.getTemuSellerReviewCount();
        boolean observed = Boolean.TRUE.equals(publication.getTemuSellerSectionObserved());
        if (!observed) return null;

        // Valoraciones escasas: señal de historial limitado, NO de ventas nulas.
        // Evitamos sumar además una segunda penalización por puntuación baja
        // sobre una muestra que puede no ser representativa.
        if (reviews != null && reviews == 0)
            return new Signal("TEMU_NO_STORE_REVIEWS",
                    "La sección de tienda indicaba cero valoraciones, no cero ventas.", 10);
        if (reviews != null && reviews < 5)
            return new Signal("TEMU_LIMITED_STORE_REVIEWS",
                    "La tienda mostraba " + reviews + " valoraciones; la muestra es muy reducida.", 10);
        if (reviews != null && reviews < MIN_REVIEWS_FOR_RATING)
            return new Signal("TEMU_LIMITED_STORE_REVIEWS",
                    "La tienda mostraba " + reviews + " valoraciones; historial todavía limitado.", 5);

        // No penalizar la calificación si no sabemos cuántas reseñas la respaldan.
        if (reviews != null && reviews >= MIN_REVIEWS_FOR_RATING && rating != null) {
            if (rating.compareTo(new BigDecimal("3.0")) < 0)
                return new Signal("TEMU_LOW_STORE_RATING",
                        "La tienda mostraba " + rating.toPlainString()
                        + "/5 sobre al menos " + MIN_REVIEWS_FOR_RATING + " valoraciones.", 15);
            if (rating.compareTo(new BigDecimal("4.0")) < 0)
                return new Signal("TEMU_MODERATE_STORE_RATING",
                        "La tienda mostraba " + rating.toPlainString()
                        + "/5 sobre al menos " + MIN_REVIEWS_FOR_RATING + " valoraciones.", 5);
        }

        if (rating == null)
            return new Signal("TEMU_SELLER_REPUTATION_UNAVAILABLE",
                    "Se identificó e inspeccionó la tienda, pero no se mostró una calificación. "
                    + "Esto no demuestra pocas ventas ni reputación negativa.", 5);

        return null;
    }
}
