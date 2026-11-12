package com.marketlens.backend.risk;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.RiskEvaluation;
import com.marketlens.backend.entity.RiskFactor;
import com.marketlens.backend.entity.RiskLevel;
import com.marketlens.backend.service.RiskCoverageCalculator;
import com.marketlens.backend.service.SellerActivityEvidence;
import com.marketlens.backend.service.PriceRiskScoring;

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

            // Niveles 3, 4 y 5: sin penalización por esta regla.
            if (reputationLevel >= 3 && reputationLevel <= 5) {
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

        // Exigir al menos 2/3 de cobertura para emitir
        // una clasificación de riesgo bajo.
        if (availableFactors * 3 < totalFactors * 2) {
            return RiskLevel.INDETERMINATE;
        }

        return RiskLevel.LOW;
    }
}