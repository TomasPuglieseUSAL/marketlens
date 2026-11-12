package com.marketlens.backend.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Solo interpreta expresiones explícitas del panel de vendedor.
 * Nunca infiere ventas desde productos publicados o desde textos ausentes.
 */
public final class SellerCommercialTextParser {

    private static final Pattern EXACT_PRODUCTS = Pattern.compile(
            "^\\s*(\\d{1,3}(?:[.,]\\d{3})+|\\d+)\\s+productos?\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern INSUFFICIENT_SALES = Pattern.compile(
            "^no\\s+(?:tiene|cuenta\\s+con)\\s+(?:las?\\s+)?"
                    + "(?:suficientes?\\s+ventas?|ventas?\\s+suficientes?)\\b.{0,110}$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);

    private static final int MAX_LISTED_PRODUCTS = 1_000_000;

    private SellerCommercialTextParser() {}

    public static Integer parseExactListedProducts(String raw) {
        if (raw == null || raw.length() > 100) {
            return null;
        }
        Matcher match = EXACT_PRODUCTS.matcher(raw);
        if (!match.matches()) {
            return null;
        }
        String digits = match.group(1).replace(".", "").replace(",", "");
        try {
            int result = Integer.parseInt(digits);
            return result <= MAX_LISTED_PRODUCTS ? result : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean explicitlyInsufficientSales(String raw) {
        if (raw == null || raw.length() > 180) {
            return false;
        }
        // No admitir textos adicionales anteriores a la declaración concreta.
        return INSUFFICIENT_SALES.matcher(raw.strip().toLowerCase(Locale.ROOT)).matches();
    }
}
