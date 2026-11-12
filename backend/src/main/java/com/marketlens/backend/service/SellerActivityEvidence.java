package com.marketlens.backend.service;

import com.marketlens.backend.entity.Seller;

import java.time.LocalDateTime;

/**
 * Interpreta observaciones de actividad comercial del vendedor.
 * No transforma productos publicados en ventas ni infiere valores ausentes.
 */
public final class SellerActivityEvidence {

    public static final int OBSERVATION_MAX_AGE_DAYS = 30;
    public static final int SMALL_CATALOG_MAX_PRODUCTS = 4;

    public enum ActivitySignal {
        RECENT_COMPLETED_SALES,
        CONFIRMED_INSUFFICIENT_SALES,
        SALES_COUNT_NOT_DISPLAYED,
        SMALL_CATALOG,
        NONE
    }

    private SellerActivityEvidence() {
    }

    public static ActivitySignal detect(Seller seller, LocalDateTime now) {
        if (seller == null) {
            return ActivitySignal.NONE;
        }

        // Dar prioridad a datos de ventas completadas realmente verificadas.
        Integer sales = seller.getCompletedSales();
        if (sales != null && sales >= 0 && isRecent(seller.getSalesObservedAt(), now)) {
            return ActivitySignal.RECENT_COMPLETED_SALES;
        }

        // Este estado solo puede provenir de una declaración explícita de la fuente.
        if (Boolean.TRUE.equals(seller.getInsufficientSalesForReputation())
                && isRecent(seller.getInsufficientSalesObservedAt(), now)) {
            return ActivitySignal.CONFIRMED_INSUFFICIENT_SALES;
        }

        // La sección de ventas fue observada correctamente, pero no mostró
        // una cantidad numérica. No asumir 0, ni usarlo ante errores de carga.
        if (isRecent(seller.getSalesCountNotDisplayedObservedAt(), now)) {
            return ActivitySignal.SALES_COUNT_NOT_DISPLAYED;
        }

        Integer listedProducts = seller.getListedProductsCount();
        if (listedProducts != null
                && listedProducts >= 1
                && listedProducts <= SMALL_CATALOG_MAX_PRODUCTS
                && isRecent(seller.getProductsObservedAt(), now)) {
            return ActivitySignal.SMALL_CATALOG;
        }

        return ActivitySignal.NONE;
    }

    private static boolean isRecent(LocalDateTime observedAt, LocalDateTime now) {
        return observedAt != null
                && !observedAt.isAfter(now)
                && !observedAt.isBefore(now.minusDays(OBSERVATION_MAX_AGE_DAYS));
    }
}
