package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionNormalizationResponse;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ExtensionNormalizationService {

    private final ExtensionImportRepository repository;

    private static final Pattern CATALOG_PATTERN = Pattern.compile(
            "(?:^|/)p/(ML[A-Z]\\d{6,})(?=/|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern LISTING_PATTERN = Pattern.compile(
            "(?:^|/)(ML[A-Z])-?(\\d{6,})(?=[/-]|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern USER_PRODUCT_PATTERN = Pattern.compile(
            "(?:^|/)up/(MLAU\\d+)(?=/|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FACEBOOK_LISTING_PATTERN = Pattern.compile(
            "^/marketplace/item/(\\d{6,30})/?$",
            Pattern.CASE_INSENSITIVE);

    public ExtensionNormalizationService(
            ExtensionImportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ExtensionNormalizationResponse normalize(
            Long importId,
            String username) {

        ExtensionImport imported = repository
                .findByIdAndImportedBy(importId, username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Importación no encontrada."));

        List<String> warnings = new ArrayList<>();

        // -------------------------
        // Normalizar moneda y precio
        // -------------------------

        String currency = imported.getCurrency();

        if (currency != null) {
            currency = currency.trim().toUpperCase(Locale.ROOT);
        }

        BigDecimal price = null;

        if (currency == null || !currency.matches("[A-Z]{3}")) {
            warnings.add(
                    "La moneda no está disponible o no es válida.");
            currency = null;
        }

        if (imported.getPriceRaw() == null ||
                imported.getPriceRaw().isBlank()) {

            warnings.add("No se obtuvo el precio.");

        } else if (currency != null) {

            price = normalizePrice(imported.getPriceRaw());

            if (price == null) {
                warnings.add(
                        "No se pudo interpretar el precio: "
                                + imported.getPriceRaw());
            }
        }

        // -------------------------
        // Identificar la referencia
        // -------------------------

        String referenceType = "UNKNOWN";
        String referenceId = null;

        try {
            URI uri = URI.create(imported.getOriginalUrl());

            String path = uri.getPath();

            if (path != null) {

                String marketplace = imported.getMarketplace();

                if ("FACEBOOK_MARKETPLACE".equals(marketplace)) {

                    String host = uri.getHost();

                    boolean validHost = host != null &&
                            (host.equalsIgnoreCase("facebook.com") ||
                                    host.toLowerCase(Locale.ROOT)
                                            .endsWith(".facebook.com"));

                    if ("https".equalsIgnoreCase(uri.getScheme())
                            && validHost) {

                        Matcher facebookListing = FACEBOOK_LISTING_PATTERN.matcher(path);

                        if (facebookListing.matches()) {
                            referenceType = "LISTING";
                            referenceId = facebookListing.group(1);
                        }
                    }

                } else if ("MERCADO_LIBRE".equals(marketplace)) {

                    Matcher userProduct = USER_PRODUCT_PATTERN.matcher(path);
                    Matcher catalog = CATALOG_PATTERN.matcher(path);
                    Matcher listing = LISTING_PATTERN.matcher(path);

                    if (userProduct.find()) {

                        referenceType = "USER_PRODUCT";
                        referenceId = userProduct.group(1)
                                .toUpperCase(Locale.ROOT);

                    } else if (catalog.find()) {

                        referenceType = "CATALOG_PRODUCT";
                        referenceId = catalog.group(1)
                                .toUpperCase(Locale.ROOT);

                    } else if (listing.find()) {

                        referenceType = "LISTING";
                        referenceId = (listing.group(1) + listing.group(2)).toUpperCase(Locale.ROOT);
                    }
                }
            }

        } catch (IllegalArgumentException exception) {
            warnings.add(
                    "No se pudo interpretar la URL original.");
        }

        // Comprobar el ID que envió el extractor.
        String extractedId = normalizeExternalId(
                imported.getExternalId(),
                imported.getMarketplace());

        if (imported.getExternalId() != null &&
                extractedId == null) {
            warnings.add(
                    "El identificador externo tiene un formato no reconocido.");
        }

        if (referenceId != null && extractedId != null &&
                !referenceId.equals(extractedId)) {

            warnings.add(
                    "El identificador extraído no coincide con el de la URL.");
        }

        if (referenceId == null && extractedId != null) {
            referenceId = extractedId;
        }

        if ("CATALOG_PRODUCT".equals(referenceType)) {
            warnings.add(
                    "El ID corresponde a un producto de catálogo, "
                            + "no necesariamente a una oferta individual.");
        }

        if ("USER_PRODUCT".equals(referenceType)) {
            warnings.add(
                    "El ID corresponde a un User Product de Mercado Libre, "
                            + "no necesariamente a una publicación individual.");
        }

        if ("UNKNOWN".equals(referenceType)) {
            warnings.add(
                    "No se pudo determinar el tipo de referencia.");
        }

        if (imported.getSellerName() == null ||
                imported.getSellerName().isBlank()) {

            warnings.add("No se identificó al vendedor.");
        }

        return new ExtensionNormalizationResponse(
                imported.getId(),
                imported.getMarketplace(),
                imported.getTitle(),
                price,
                currency,
                referenceType,
                referenceId,
                imported.getSellerName(),
                warnings);
    }

    private String normalizeExternalId(
            String raw,
            String marketplace) {

        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.trim();

        if ("FACEBOOK_MARKETPLACE".equals(marketplace)) {
            return normalized.matches("\\d{6,30}")
                    ? normalized
                    : null;
        }

        if ("MERCADO_LIBRE".equals(marketplace)) {

            normalized = normalized
                    .replace("-", "")
                    .toUpperCase(Locale.ROOT);

            return normalized.matches(
                    "(MLAU\\d{6,}|ML[A-Z]\\d{6,})") ? normalized : null;
        }

        return null;
    }

    private BigDecimal normalizePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim()
                .replace(" ", "")
                .replace("\u00A0", "");

        String normalized;

        // Ejemplo: 1.250,50 o 250.000
        if (value.matches(
                "\\d{1,3}(\\.\\d{3})+(,\\d{1,2})?")) {

            normalized = value
                    .replace(".", "")
                    .replace(',', '.');

            // Ejemplo: 1,250.50
        } else if (value.matches(
                "\\d{1,3}(,\\d{3})+(\\.\\d{1,2})?")) {

            normalized = value.replace(",", "");

            // Ejemplo: 1250,50
        } else if (value.matches("\\d+,\\d{1,2}")) {

            normalized = value.replace(',', '.');

            // Ejemplo: 1250 o 1250.50
        } else if (value.matches("\\d+(\\.\\d{1,2})?")) {

            normalized = value;

        } else {
            return null;
        }

        try {
            BigDecimal result = new BigDecimal(normalized)
                    .setScale(2, RoundingMode.UNNECESSARY);

            if (result.signum() <= 0 || result.precision() > 18) {
                return null;
            }

            return result;

        } catch (ArithmeticException | NumberFormatException exception) {
            return null;
        }
    }
}