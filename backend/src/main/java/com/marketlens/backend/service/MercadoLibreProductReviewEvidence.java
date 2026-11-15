package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Calificaciones del PRODUCTO Mercado Libre, potencialmente compartidas
 * entre varios vendedores. No equivalen a la reputación del vendedor
 * ni certifican compras o autenticidad. Por ahora no alteran el puntaje.
 */
public final class MercadoLibreProductReviewEvidence {

    public record Signal(String code, String description, int points) { }

    private static final int MAX_AGE_DAYS = 30;

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
        LocalDateTime observedAt = publication.getMlProductObservedAt();
        return observedAt != null && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusDays(MAX_AGE_DAYS));
    }

    public static Signal evaluate(Publication publication, LocalDateTime now) {
        if (!hasObservedEvidence(publication, now)) return null;
        return new Signal("ML_PRODUCT_REVIEWS_OBSERVED",
                "El producto muestra " + publication.getMlProductRating().toPlainString()
                + "/5 con " + publication.getMlProductReviewCount()
                + " opiniones en Mercado Libre. Son valoraciones del producto, "
                + "no de la fiabilidad del vendedor.", 0);
    }
}
