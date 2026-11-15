package com.marketlens.backend.risk;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskFactor;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.service.RiskCoverageCalculator;
import com.marketlens.backend.service.SellerActivityEvidence;
import com.marketlens.backend.service.PriceRiskScoring;
import com.marketlens.backend.service.TemuSellerRiskScoring;
import com.marketlens.backend.service.TemuProductReviewEvidence;
import com.marketlens.backend.service.MercadoLibreProductReviewEvidence;
import com.marketlens.backend.service.MercadoLibreSellerEvidence;
import com.marketlens.backend.service.TemuCommercialEvidence;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

@Component
public class RiskEngine {

    // Umbrales exploratorios, pendientes de validación empírica.
    private static final int SALES_OBSERVATION_MAX_AGE_DAYS =
            SellerActivityEvidence.OBSERVATION_MAX_AGE_DAYS;

    private final RiskCoverageCalculator coverageCalculator;

    public RiskEngine(RiskCoverageCalculator coverageCalculator) {
        this.coverageCalculator = coverageCalculator;
    }

    /** Mantiene compatibilidad con las evaluaciones y pruebas anteriores. */
    public RiskEvaluation evaluate(Publication publication) {
        return evaluate(publication, null, null, 0);
    }

    /** Incorpora un análisis de precio ya calculado por el servicio de aplicación. */
    public RiskEvaluation evaluate(
            Publication publication,
            Integer priceRiskPoints,
            BigDecimal referenceMedian,
            int referenceCount) {

        RiskEvaluation evaluation = new RiskEvaluation();

        evaluation.setPublication(publication);

        // 1. Calcular qué información está disponible.
        coverageCalculator.applyCoverage(
                publication,
                evaluation);

        // 2. Ejecutar las reglas existentes.
        int score = 0;

        score += evaluateSellerReputation(
                publication,
                evaluation);

        score += evaluateLimitedSellerHistory(publication, evaluation);

        score += evaluateLimitedSellerActivity(publication, evaluation);

        score += evaluateSellerAccountAge(
                publication,
                evaluation);

        score += evaluateDescription(
                publication,
                evaluation);

        score += evaluatePriceAnomaly(
                publication, evaluation, priceRiskPoints,
                referenceMedian, referenceCount);

        // Una única regla Temu evita duplicar señales de historial/valoración.
        TemuSellerRiskScoring.Signal temuSignal =
                TemuSellerRiskScoring.evaluate(publication, LocalDateTime.now());
        if (temuSignal != null) {
            addFactor(evaluation, temuSignal.code(),
                    temuSignal.description(), temuSignal.points());
            score += temuSignal.points();
        }

        // Opiniones observadas del PRODUCTO: independientes de la tienda
        // y no interpretadas como ventas. Una puntuación buena no resta riesgo.
        TemuProductReviewEvidence.Signal productSignal =
                TemuProductReviewEvidence.evaluate(publication, LocalDateTime.now());
        if (productSignal != null) {
            addFactor(evaluation, productSignal.code(),
                    productSignal.description(), productSignal.points());
            score += productSignal.points();
        }

        // Opiniones del catálogo de Mercado Libre: fuente distinta de la
        // reputación de cada vendedor. Sólo contexto informativo (0 puntos).
        MercadoLibreProductReviewEvidence.Signal mlSignal =
                MercadoLibreProductReviewEvidence.evaluate(publication, LocalDateTime.now());
        if (mlSignal != null) {
            addFactor(evaluation, mlSignal.code(), mlSignal.description(), mlSignal.points());
        }
        // Reputación observada (sin OAuth): puntuar una sola vez si NO
        // existe un nivel verificado. Nunca sumarla duplicada.
        MercadoLibreSellerEvidence.Signal mlSellerSignal =
                MercadoLibreSellerEvidence.evaluateObserved(publication, LocalDateTime.now());
        if (mlSellerSignal != null) {
            addFactor(evaluation, mlSellerSignal.code(),
                    mlSellerSignal.description(), mlSellerSignal.points());
            score += mlSellerSignal.points();
        }

        // Antigüedad aproximada y ventas mostradas de la TIENDA,
        // independientes de las ventas observadas del PRODUCTO.
        // Nunca interpretarlas como transacciones verificadas; no cambian la cobertura.
        TemuCommercialEvidence.Signal tenureSignal =
                TemuCommercialEvidence.evaluateStoreAge(publication, LocalDateTime.now());
        if (tenureSignal != null) {
            addFactor(evaluation, tenureSignal.code(),
                    tenureSignal.description(), tenureSignal.points());
            score += tenureSignal.points();
        }
        TemuCommercialEvidence.Signal storeSalesSignal =
                TemuCommercialEvidence.evaluateStoreSales(publication, LocalDateTime.now());
        if (storeSalesSignal != null) {
            addFactor(evaluation, storeSalesSignal.code(),
                    storeSalesSignal.description(), storeSalesSignal.points());
            score += storeSalesSignal.points();
        }
        TemuCommercialEvidence.Signal productSalesSignal =
                TemuCommercialEvidence.evaluateProductSales(publication, LocalDateTime.now());
        // Si la TIENDA ya recibió una penalización por pocas ventas,
        // no sumar otra por pocas ventas del producto actual.
        if (productSalesSignal != null && storeSalesSignal != null
                && storeSalesSignal.points() > 0 && productSalesSignal.points() > 0) {
            productSalesSignal = new TemuCommercialEvidence.Signal(
                    "TEMU_PRODUCT_SALES_OBSERVED",
                    productSalesSignal.description()
                    + " No se suma otra penalización por la actividad del mismo vendedor.",
                    0);
        }
        if (productSalesSignal != null) {
            addFactor(evaluation, productSalesSignal.code(),
                    productSalesSignal.description(), productSalesSignal.points());
            score += productSalesSignal.points();
        }

        // Valor observado junto al nombre de la tienda; no usar aggregateRating
        // del PRODUCTO. El peso estadístico requiere más validación.
        if (publication.getMarketplace() == Marketplace.TEMU
                && publication.getSeller() != null
                && publication.getTemuSellerRating() != null) {
            addFactor(evaluation, "TEMU_SELLER_RATING_OBSERVED",
                    "La tienda aparece con una calificación de "
                    + publication.getTemuSellerRating().toPlainString()
                    + "/5 en Temu. Es una señal observada, no una verificación de MarketLens.",
                    0);
        }

        // Distintivo observado: dato contextual, NO reputación contrastada.
        // No otorga cobertura adicional ni reduce automáticamente el puntaje.
        if (publication.getMarketplace() == Marketplace.TEMU
                && "TEMU_STAR_SELLER".equals(publication.getSellerBadge())) {
            addFactor(evaluation, "TEMU_STAR_SELLER_OBSERVED",
                    "La página de Temu mostraba la etiqueta Star Seller para la tienda. "
                    + "MarketLens no ha verificado sus criterios ni que garantice una compra segura.",
                    0);
        }

        score = Math.min(score, 100);

        // 3. Guardar los resultados.
        evaluation.setScore(score);

        evaluation.setLevel(
                determineLevel(score, evaluation));

        return evaluation;
    }

    private int evaluateSellerReputation(
            Publication publication,
            RiskEvaluation evaluation) {

        if (publication.getSeller() == null) {
            return 0;
        }

        // Primero: reputación de Mercado Libre (niveles 1 a 5).
        Integer reputationLevel = publication.getSeller().getReputationLevel();

        if (reputationLevel != null) {

            if (reputationLevel == 1) {
                addFactor(
                        evaluation,
                        "LOW_SELLER_REPUTATION",
                        "El vendedor presenta el nivel más bajo de reputación.",
                        25);

                return 25;
            }

            if (reputationLevel == 2) {
                addFactor(
                        evaluation,
                        "LOW_SELLER_REPUTATION",
                        "El vendedor presenta un nivel bajo de reputación.",
                        15);

                return 15;
            }

            // El nivel 3 refleja una reputación intermedia: menor señal
            // de riesgo que 1–2, pero no debería ser neutral.
            if (reputationLevel == 3) {
                addFactor(evaluation, "MODERATE_SELLER_REPUTATION",
                        "El vendedor presenta una reputación intermedia (3/5).",
                        MercadoLibreSellerEvidence.INTERMEDIATE_REPUTATION_POINTS);
                return MercadoLibreSellerEvidence.INTERMEDIATE_REPUTATION_POINTS;
            }

            // Niveles buenos 4 y 5: sin penalización.
            if (reputationLevel == 4 || reputationLevel == 5) {
                return 0;
            }

            // Nivel inválido: no utilizarlo.
            return 0;
        }

        // Compatibilidad con reputaciones antiguas entre 0 y 1.
        BigDecimal reputation = publication.getSeller().getReputation();

        if (reputation != null &&
                reputation.compareTo(BigDecimal.ZERO) >= 0 &&
                reputation.compareTo(BigDecimal.ONE) <= 0 &&
                reputation.compareTo(new BigDecimal("0.30")) < 0) {

            addFactor(
                    evaluation,
                    "LOW_SELLER_REPUTATION",
                    "El vendedor presenta una reputación baja.",
                    25);

            return 25;
        }

        return 0;
    }

    private int evaluateLimitedSellerHistory(
            Publication publication,
            RiskEvaluation evaluation) {

        if (publication.getSeller() == null) {
            return 0;
        }

        Integer sales = publication.getSeller().getCompletedSales();
        LocalDateTime observedAt = publication.getSeller().getSalesObservedAt();

        // Sin observación numérica reciente, no inferir que tiene cero ventas.
        if (sales == null || sales < 0 || observedAt == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (observedAt.isAfter(now)
                || observedAt.isBefore(now.minusDays(SALES_OBSERVATION_MAX_AGE_DAYS))) {
            return 0;
        }

        // Escala experimental: cero verificado > ausencia de historial
        // verificable > historial bajo con ventas verificadas.
        int contribution;
        String code;
        String description;

        if (sales == 0) {
            contribution = 20;
            code = "ZERO_VERIFIED_SALES";
            description = "Se verificaron cero ventas completadas en la última "
                    + "observación. El vendedor no dispone de un historial "
                    + "de ventas completadas comprobado.";
        } else if (sales <= 4) {
            contribution = 15;
            code = "LIMITED_SELLER_HISTORY";
            description = "El vendedor registraba " + sales
                    + (sales == 1 ? " venta" : " ventas")
                    + " en la última observación disponible. "
                    + "Su historial comercial es reducido.";
        } else if (sales <= 9) {
            contribution = 5;
            code = "LIMITED_SELLER_HISTORY";
            description = "El vendedor registraba " + sales
                    + " ventas en la última observación disponible. "
                    + "Su historial comercial todavía es reducido.";
        } else {
            return 0;
        }

        addFactor(evaluation, code, description, contribution);
        return contribution;
    }

    /**
     * Actividad comercial observada sin ventas numéricas recientes ni
     * reputación consolidada. La ausencia de una cifra NO equivale a 0 ventas.
     */
    private int evaluateLimitedSellerActivity(
            Publication publication,
            RiskEvaluation evaluation) {

        if (publication.getSeller() == null) {
            return 0;
        }

        var seller = publication.getSeller();

        Integer reputationLevel = seller.getReputationLevel();
        boolean hasReputationLevel = reputationLevel != null
                && reputationLevel >= 1 && reputationLevel <= 5;

        BigDecimal reputation = seller.getReputation();
        boolean hasLegacyReputation = reputation != null
                && reputation.compareTo(BigDecimal.ZERO) >= 0
                && reputation.compareTo(BigDecimal.ONE) <= 0;

        // Si hay reputación verificable, no calificar el historial como
        // "no verificable" por el mero hecho de que falte una cifra de ventas.
        if (hasReputationLevel || hasLegacyReputation) {
            return 0;
        }

        // Las ventas numéricas recientes, incluso 0, tienen prioridad.
        SellerActivityEvidence.ActivitySignal signal =
                SellerActivityEvidence.detect(seller, LocalDateTime.now());

        String description;
        switch (signal) {
            case CONFIRMED_INSUFFICIENT_SALES ->
                    description = "La plataforma indica expresamente que el "
                            + "vendedor no cuenta con ventas suficientes para "
                            + "asignarle una reputación. No se conoce la "
                            + "cantidad exacta de ventas completadas.";

            case SALES_COUNT_NOT_DISPLAYED ->
                    description = "Se consultó correctamente la sección "
                            + "comercial del vendedor, pero no se mostró una "
                            + "cifra de ventas ni una reputación consolidada. "
                            + "Esto no implica que tenga cero ventas.";

            case SMALL_CATALOG -> {
                int count = seller.getListedProductsCount();
                description = "El vendedor tenía " + count
                        + (count == 1 ? " producto publicado" : " productos publicados")
                        + " en la última observación, sin ventas "
                        + "verificables ni reputación consolidada. "
                        + "Los productos publicados no equivalen a ventas.";
            }

            // RECENT_COMPLETED_SALES fue tratado por la regla numérica.
            // NONE significa que no hay evidencia válida para penalizar.
            default -> {
                return 0;
            }
        }

        addFactor(
                evaluation,
                "UNVERIFIED_SELLER_HISTORY",
                description,
                15);
        return 15;
    }

    private int evaluateSellerAccountAge(
            Publication publication,
            RiskEvaluation evaluation) {

        // -------------------------------------
        // 1. Antigüedad exacta, si está disponible
        // -------------------------------------

        if (publication.getSeller() != null) {

            Integer ageDays = publication.getSeller().getAccountAgeDays();

            if (ageDays != null && ageDays >= 0) {

                if (ageDays < 30) {
                    addFactor(
                            evaluation,
                            "NEW_SELLER_ACCOUNT",
                            "La cuenta del vendedor tiene menos de 30 días de antigüedad.",
                            20);

                    return 20;
                }

                return 0;
            }
        }

        // -------------------------------------
        // 2. Año de incorporación a Facebook
        // -------------------------------------

        if (publication.getMarketplace() == Marketplace.FACEBOOK_MARKETPLACE) {

            Integer joinYear = publication.getFacebookJoinYear();

            int currentYear = java.time.Year.now().getValue();

            if (joinYear != null &&
                    joinYear >= 2004 &&
                    joinYear == currentYear) {

                addFactor(
                        evaluation,
                        "RECENT_FACEBOOK_ACCOUNT",
                        "La cuenta del vendedor se incorporó a Facebook durante el año actual.",
                        10);

                return 10;
            }
        }

        return 0;
    }

    private int evaluateDescription(
            Publication publication,
            RiskEvaluation evaluation) {

        String description = publication.getDescription();

        if (description == null) {
            return 0;
        }

        String normalized = description.toLowerCase(Locale.ROOT);

        if (normalized.contains("whatsapp")
                || normalized.contains("transferencia")
                || normalized.contains("urgente")) {

            addFactor(
                    evaluation,
                    "SUSPICIOUS_TEXT",
                    "La descripción contiene expresiones asociadas a posibles señales de riesgo.",
                    15);

            return 15;
        }

        return 0;
    }

    private int evaluatePriceAnomaly(
            Publication publication,
            RiskEvaluation evaluation,
            Integer riskPoints,
            BigDecimal referenceMedian,
            int referenceCount) {

        if (riskPoints == null || riskPoints <= 0
                || referenceCount < PriceRiskScoring.MIN_REFERENCES
                || referenceMedian == null || referenceMedian.signum() <= 0
                || publication.getPrice() == null || publication.getPrice().signum() <= 0) {
            return 0;
        }

        // Comprobación defensiva: el puntaje no se acepta a ciegas.
        Integer expectedPoints = PriceRiskScoring.score(
                publication.getPrice(), referenceMedian, referenceCount);
        if (!riskPoints.equals(expectedPoints)) {
            return 0;
        }

        BigDecimal discountPercent = BigDecimal.valueOf(100).subtract(
                publication.getPrice()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(referenceMedian, 2, RoundingMode.HALF_UP));

        addFactor(
                evaluation,
                "UNUSUALLY_LOW_PRICE",
                "El precio publicado está un " + discountPercent.toPlainString()
                        + "% por debajo de la mediana de "
                        + referenceMedian.toPlainString() + " ARS, calculada sobre "
                        + referenceCount + " ofertas distintas.",
                riskPoints);
        return riskPoints;
    }

    private void addFactor(
            RiskEvaluation evaluation,
            String code,
            String description,
            int contribution) {

        RiskFactor factor = new RiskFactor();

        factor.setEvaluation(evaluation);
        factor.setCode(code);
        factor.setDescription(description);
        factor.setScoreContribution(contribution);

        evaluation.getFactors().add(factor);
    }

    private RiskLevel determineLevel(
            int score,
            RiskEvaluation evaluation) {

        // Las señales detectadas mantienen su gravedad.
        if (score >= 60) {
            return RiskLevel.HIGH;
        }

        if (score >= 30) {
            return RiskLevel.MEDIUM;
        }

        Integer availableFactors = evaluation.getAvailableFactors();

        Integer totalFactors = evaluation.getTotalFactors();

        // Sin información de cobertura, no clasificamos
        // automáticamente como riesgo bajo.
        if (availableFactors == null ||
                totalFactors == null ||
                totalFactors <= 0) {

            return RiskLevel.INDETERMINATE;
        }

        // Exigir al menos 2/3 de la cobertura aplicable para emitir
        // una clasificación de riesgo bajo.
        if (availableFactors * 3 < totalFactors * 2) {
            return RiskLevel.INDETERMINATE;
        }

        // Temu: la cobertura 3/3 es disponibilidad, no necesariamente calidad.
        // Una reputación de tienda respaldada por ID y >=10 reseñas sigue
        // permitiendo LOW con cobertura suficiente, como anteriormente.
        // En cobertura COMPLETA, un puntaje 0 también puede clasificarse
        // como LOW cuando tanto la tienda como el producto tienen señales
        // positivas actuales (el producto requiere >=10 opiniones).
        Publication publication = evaluation.getPublication();
        // Las opiniones compartidas del catálogo no permiten calificar LOW
        // cuando falta por completo evidencia del vendedor en Mercado Libre.
        if (publication != null && publication.getMarketplace() == Marketplace.MERCADO_LIBRE
                && !RiskCoverageCalculator.hasSellerEvidence(publication, LocalDateTime.now())) {
            return RiskLevel.INDETERMINATE;
        }
        if (publication != null && publication.getMarketplace() == Marketplace.TEMU) {
            LocalDateTime now = LocalDateTime.now();
            if (!TemuSellerRiskScoring.hasSupportedReputation(publication, now)
                    && !hasCompletePositiveTemuEvidence(publication, evaluation, score, now)) {
                return RiskLevel.INDETERMINATE;
            }
        }

        return RiskLevel.LOW;
    }

    private boolean hasCompletePositiveTemuEvidence(
            Publication publication,
            RiskEvaluation evaluation,
            int score,
            LocalDateTime now) {
        // Esta ruta NO habilita LOW para calificaciones sin muestra, poco
        // favorables, observaciones vencidas o cobertura apenas parcial.
        if (score != 0
                || evaluation.getAvailableFactors() == null
                || evaluation.getTotalFactors() == null
                || !evaluation.getAvailableFactors().equals(evaluation.getTotalFactors())
                || !TemuSellerRiskScoring.hasObservedReputation(publication, now)
                || !TemuProductReviewEvidence.hasObservedEvidence(publication, now)) {
            return false;
        }

        return publication.getTemuSellerRating().compareTo(new BigDecimal("4.0")) >= 0
                && publication.getTemuProductRating().compareTo(new BigDecimal("4.0")) >= 0
                && publication.getTemuProductReviewCount() >= 10;
    }
}