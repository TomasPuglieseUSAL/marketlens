package com.marketlens.backend.service;

import java.math.BigDecimal;

/**
 * Regla experimental de precio: penaliza únicamente precios muy inferiores
 * a una mediana calculada con suficientes publicaciones independientes.
 * No depende de marcas ni categorías de productos.
 */
public final class PriceRiskScoring {

    public static final int MIN_REFERENCES = 5;

    private PriceRiskScoring() {
    }

    /**
     * @return null si faltan datos; 0 si no hay anomalía; 5, 15 o 25 si la hay.
     */
    public static Integer score(
            BigDecimal publicationPrice,
            BigDecimal medianReferencePrice,
            int referenceCount) {

        if (referenceCount < MIN_REFERENCES
                || publicationPrice == null
                || medianReferencePrice == null
                || publicationPrice.signum() <= 0
                || medianReferencePrice.signum() <= 0) {
            return null;
        }

        BigDecimal priceTimes100 = publicationPrice.multiply(BigDecimal.valueOf(100));

        // Comparar directamente los productos cruzados evita que un redondeo
        // al calcular porcentajes cambie el tramo en los límites.
        if (priceTimes100.compareTo(medianReferencePrice.multiply(BigDecimal.valueOf(70))) >= 0) {
            return 0;
        }
        if (priceTimes100.compareTo(medianReferencePrice.multiply(BigDecimal.valueOf(60))) >= 0) {
            return 5;
        }
        if (priceTimes100.compareTo(medianReferencePrice.multiply(BigDecimal.valueOf(50))) >= 0) {
            return 15;
        }
        return 25;
    }
}
