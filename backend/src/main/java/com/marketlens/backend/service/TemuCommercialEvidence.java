package com.marketlens.backend.service;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Actividad comercial OBSERVADA, no verificada:
 * - antigüedad aproximada de la tienda ("1 year on Temu")
 * - ventas aproximadas del PRODUCTO ("15K+ sold"), nunca ventas del vendedor.
 * No modifica los tres factores de cobertura.
 */
public final class TemuCommercialEvidence {
    private static final int MAX_OBSERVATION_AGE_DAYS = 30;
    private static final Pattern TENURE = Pattern.compile(
            "^([1-9][0-9]{0,2})\\s+(day|week|month|year)s?\\s+on\\s+Temu$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNDER_TENURE = Pattern.compile(
            "^(?:under|less than)\\s+(?:1|one)\\s+(day|week|month)\\s+on\\s+Temu$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SALES = Pattern.compile(
            "^([0-9]{1,8}(?:[.,][0-9])?)\\s*([kKmM])?(\\+)?\\s+sold$",
            Pattern.CASE_INSENSITIVE);

    public record AgeRange(int lowerDays, int upperDays) { }
    public record SalesLowerBound(int minimumUnits, boolean approximate) { }
    public record Signal(String code, String description, int points) { }

    private TemuCommercialEvidence() { }

    public static AgeRange parseStoreTenure(String text) {
        if (text == null) return null;
        String value = text.trim();
        Matcher under = UNDER_TENURE.matcher(value);
        if (under.matches()) {
            return switch (under.group(1).toLowerCase(java.util.Locale.ROOT)) {
                case "day" -> new AgeRange(0, 0);
                case "week" -> new AgeRange(0, 6);
                case "month" -> new AgeRange(0, 29);
                default -> null;
            };
        }
        Matcher match = TENURE.matcher(value);
        if (!match.matches()) return null;
        int n = Integer.parseInt(match.group(1));
        return switch (match.group(2).toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> new AgeRange(n, n);
            case "week" -> new AgeRange(n * 7, n * 7 + 6);
            case "month" -> new AgeRange(n * 30, n * 30 + 29);
            case "year" -> new AgeRange(n * 365, n * 365 + 364);
            default -> null;
        };
    }

    public static SalesLowerBound parseProductSales(String text) {
        if (text == null) return null;
        Matcher match = SALES.matcher(text.trim());
        if (!match.matches()) return null;
        BigDecimal n;
        try {
            n = new BigDecimal(match.group(1).replace(',', '.'));
        } catch (NumberFormatException ex) {
            return null;
        }
        String unit = match.group(2);
        boolean plus = match.group(3) != null;
        int factor = unit == null ? 1 :
                "k".equalsIgnoreCase(unit) ? 1000 : 1000000;
        if (unit == null && n.stripTrailingZeros().scale() > 0) return null;
        BigDecimal units = n.multiply(BigDecimal.valueOf(factor));
        if (units.compareTo(BigDecimal.valueOf(100000000)) > 0
                || units.stripTrailingZeros().scale() > 0) return null;
        return new SalesLowerBound(units.intValueExact(), plus || unit != null);
    }

    private static boolean fresh(LocalDateTime at, LocalDateTime now) {
        return at != null && !at.isAfter(now)
                && !at.isBefore(now.minusDays(MAX_OBSERVATION_AGE_DAYS));
    }

    public static Signal evaluateStoreAge(Publication publication, LocalDateTime now) {
        if (publication == null || publication.getMarketplace() != Marketplace.TEMU
                || publication.getSeller() == null
                || publication.getSeller().getName() == null
                || publication.getSeller().getName().isBlank()
                || !fresh(publication.getTemuStoreTenureObservedAt(), now)) return null;

        AgeRange range = parseStoreTenure(publication.getTemuStoreTenureText());
        if (range == null) return null;
        long elapsedDays = Math.max(0,
                ChronoUnit.DAYS.between(publication.getTemuStoreTenureObservedAt(), now));
        long maxAgeNow = range.upperDays() + elapsedDays;
        String description = "Temu muestra \"" + publication.getTemuStoreTenureText()
                + "\" para la tienda. Es una antigüedad aproximada observada, "
                + "no una fecha de creación verificada.";

        // Una fecha oficial en Seller, si se conoce, ya se evaluó en RiskEngine.
        if (publication.getSeller().getAccountAgeDays() != null)
            return new Signal("TEMU_STORE_TENURE_OBSERVED", description, 0);
        if (maxAgeNow < 7)
            return new Signal("TEMU_VERY_RECENT_STORE", description
                    + " La tienda tiene menos de una semana según la estimación.", 15);
        if (maxAgeNow < 30)
            return new Signal("TEMU_RECENT_STORE", description
                    + " La tienda tiene menos de un mes según la estimación.", 8);
        return new Signal("TEMU_STORE_TENURE_OBSERVED", description, 0);
    }

    public static Signal evaluateProductSales(Publication publication, LocalDateTime now) {
        if (publication == null || publication.getMarketplace() != Marketplace.TEMU
                || !fresh(publication.getTemuProductSalesObservedAt(), now)) return null;
        SalesLowerBound sales = parseProductSales(publication.getTemuProductSoldText());
        if (sales == null) return null;
        String description = "Temu muestra \"" + publication.getTemuProductSoldText()
                + "\" para este producto (no para la tienda). "
                + "No son ventas verificadas por MarketLens.";
        // Una cifra acompañada de +/K/M sólo es un piso; nunca concluir
        // que hubo pocas ventas a partir de un piso aproximado.
        if (sales.approximate())
            return new Signal("TEMU_PRODUCT_SALES_OBSERVED", description, 0);

        // Evitar una penalización adicional cuando ya hay evidencia de
        // opiniones del producto. Pocas ventas de una publicación nueva
        // no equivalen por sí solas a una tienda fraudulenta.
        if (TemuProductReviewEvidence.hasObservedEvidence(publication, now))
            return new Signal("TEMU_PRODUCT_SALES_OBSERVED", description, 0);

        if (sales.minimumUnits() < 5)
            return new Signal("TEMU_VERY_FEW_PRODUCT_SALES", description
                    + " Indicio leve de actividad todavía muy limitada.", 5);
        if (sales.minimumUnits() < 10)
            return new Signal("TEMU_FEW_PRODUCT_SALES", description
                    + " Indicio leve de actividad limitada.", 3);
        return new Signal("TEMU_PRODUCT_SALES_OBSERVED", description, 0);
    }
}
