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
