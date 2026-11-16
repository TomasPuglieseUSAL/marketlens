package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.time.LocalDateTime;

/**
 * Nivel de termómetro visible en la página, separado del nivel de reputación
 * verificado por OAuth. Nunca se promueve silenciosamente a Seller.reputationLevel.
 */
public final class MercadoLibreSellerEvidence {
    private static final int MAX_AGE_DAYS = 30;
    // Escala experimental, pendiente de validación con publicaciones reales.
    // 3/5 es peor que 4–5/5, pero claramente menos grave que 1–2/5.
    public static final int INTERMEDIATE_REPUTATION_POINTS = 8;

    public record Signal(String code, String description, int points) { }

    public static int pointsForLevel(int level) {
        return switch (level) {
            case 1 -> 25;
            case 2 -> 15;
            case 3 -> INTERMEDIATE_REPUTATION_POINTS;
            default -> 0;
        };
    }

    public static Signal evaluateObserved(Publication publication, LocalDateTime now) {
        if (!hasObservedReputation(publication, now)
                || publication.getSeller().getReputationLevel() != null) return null;
        int level = publication.getMlSellerReputationObserved();
        int points = pointsForLevel(level);
        return new Signal("ML_SELLER_REPUTATION_OBSERVED",
                "El termómetro público del vendedor muestra " + level
                + "/5. Es una observación de Mercado Libre, no corroborada por OAuth.", points);
    }

    private MercadoLibreSellerEvidence() { }

    public static boolean hasObservedReputation(Publication publication, LocalDateTime now) {
        if (publication == null || publication.getMarketplace() != Marketplace.MERCADO_LIBRE
                || publication.getSeller() == null) return false;
        Integer level = publication.getMlSellerReputationObserved();
        LocalDateTime observedAt = publication.getMlSellerReputationObservedAt();
        return level != null && level >= 1 && level <= 5 && observedAt != null
                && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusDays(MAX_AGE_DAYS));
    }
}
