package com.marketlens.backend.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solo convierte cantidades EXPLÍCITAS y no aproximadas.
 * Ejemplos válidos: "4 ventas", "4 ventas totales", "1.200 ventas".
 * Ejemplos rechazados: "+4 ventas", "más de 4 ventas", "4 ventas en los últimos 60 días".
 */
public final class SellerSalesTextParser {

    private static final Pattern EXACT = Pattern.compile(
            "^([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)\\s+ventas?" +
            "(?:\\s+(?:totales|realizadas|completadas|concretadas))?$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private SellerSalesTextParser() {}

    public static boolean isApproximate(String raw) {
        if (raw == null) return false;
        String value = raw.strip().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("+")
                || value.startsWith("más de ")
                || value.startsWith("mas de ")
                || value.startsWith("al menos ")
                || value.startsWith("aprox")
                || value.contains("últimos ")
                || value.contains("ultimos ")
                || value.contains("últimas ")
                || value.contains("ultimas ");
    }

    public static Integer parseExact(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 120) return null;
        String normalized = raw.strip().replaceAll("\\s+", " ");
        Matcher match = EXACT.matcher(normalized);
        if (!match.matches()) return null;
        String digits = match.group(1).replace(".", "").replace(",", "");
        try {
            long number = Long.parseLong(digits);
            return number <= Integer.MAX_VALUE ? (int) number : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
