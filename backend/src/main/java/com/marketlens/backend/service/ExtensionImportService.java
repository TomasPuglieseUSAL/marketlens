package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import com.marketlens.backend.dto.ExtensionImportResponse;

import java.net.URI;
import java.time.Year;

import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.repository.ExtensionImportRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExtensionImportService {

    private final ExtensionImportRepository repository;

    public ExtensionImportService(
            ExtensionImportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExtensionImportResponse importPublication(
            ExtensionPreviewRequest request,
            String username) {

        if (!"MERCADO_LIBRE".equals(request.marketplace()) &&
                !"FACEBOOK_MARKETPLACE".equals(request.marketplace()) &&
                !"TEMU".equals(request.marketplace())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Marketplace no soportado.");
        }

        // Validaciones de longitud acordes a las columnas.
        validateLength(request.marketplace(), 40, "marketplace");
        validateLength(request.title(), 500, "title");
        validateLength(request.price(), 100, "price");
        validateLength(request.currency(), 10, "currency");
        validateLength(request.seller(), 255, "seller");
        validateLength(request.externalId(), 120, "externalId");
        validateLength(request.description(), 20000, "description");
        validateLength(request.url(), 2048, "url");
        validateLength(username, 255, "username");
        validateLength(request.selectedOfferId(), 120, "selectedOfferId");

        // No sustituir la identidad del producto del catálogo por un MLA
        // individual sin una selección explícita y consistente.
        String selectedOfferId = normalizeOptional(request.selectedOfferId());
        if (selectedOfferId != null) {
            String url = request.url();
            String externalId = request.externalId();
            boolean isCatalog = false;
            try {
                URI catalog = URI.create(url);
                String host = catalog.getHost();
                isCatalog = "https".equalsIgnoreCase(catalog.getScheme())
                        && host != null
                        && (host.equalsIgnoreCase("mercadolibre.com.ar")
                            || host.toLowerCase(java.util.Locale.ROOT).endsWith(".mercadolibre.com.ar"))
                        && catalog.getPath() != null
                        && catalog.getPath().matches(
                            "(?i)^/(?:[^/]+/)?p/MLA\\d{6,}/?$");
            } catch (IllegalArgumentException ignored) {
                // Se rechaza con el mismo mensaje que una referencia incoherente.
            }
            if (!"MERCADO_LIBRE".equals(request.marketplace())
                    || !isCatalog
                    || !selectedOfferId.matches("MLA[1-9]\\d{5,}")
                    || !selectedOfferId.equalsIgnoreCase(externalId == null ? "" : externalId)
                    || request.seller() == null || request.seller().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La oferta seleccionada no coincide con un ID individual de Mercado Libre, vendedor y catálogo válidos.");
            }
        }

        if ("FACEBOOK_MARKETPLACE".equals(request.marketplace())) {
            validateFacebookUrl(request.url());
        }

        ExtensionImport imported = new ExtensionImport(
                request.marketplace(),
                request.title().trim(),
                normalizeOptional(request.price()),
                normalizeOptional(request.currency()),
                normalizeOptional(request.seller()),
                normalizeOptional(request.externalId()),
                normalizeOptional(request.description()),
                request.url().trim(),
                username);
        imported.setSelectedOfferId(selectedOfferId);

        Long externalSellerId = null;

        String rawSellerId = request.sellerId();

        if (rawSellerId != null && !rawSellerId.isBlank()) {

            if (!rawSellerId.matches("[1-9][0-9]*")) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El ID del vendedor no es válido.");
            }

            try {
                externalSellerId = Long.parseLong(rawSellerId);

            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El ID del vendedor excede el rango permitido.");
            }
        }

        imported.setExternalSellerId(externalSellerId);

        if ("TEMU".equals(request.marketplace()) && request.seller() != null
                && request.seller().trim().matches(
                   "(?i)^[0-5](?:[.,]\\d)?\\s+out\\s+of\\s+(?:five|5)\\s+stars?\\s*$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El nombre de la tienda no puede ser una calificación del producto.");
        }
        java.math.BigDecimal temuRating = request.temuSellerRating();
        if (temuRating != null) {
            if (!"TEMU".equals(request.marketplace()) ||
                    request.seller() == null || request.seller().isBlank() ||
                    temuRating.compareTo(java.math.BigDecimal.ZERO) <= 0 ||
                    temuRating.compareTo(new java.math.BigDecimal("5.0")) > 0 ||
                    temuRating.scale() > 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La puntuación de tienda Temu no es válida.");
            }
        }
        imported.setTemuSellerRating(temuRating);

        Integer reviewCount = request.temuSellerReviewCount();
        String storeId = request.temuStoreId();
        Boolean sectionObserved = request.temuSellerSectionObserved();
        boolean anyTemuObservation = temuRating != null || reviewCount != null
                || storeId != null || sectionObserved != null;
        if (anyTemuObservation && !"TEMU".equals(request.marketplace())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los datos de tienda Temu no corresponden a este marketplace.");
        }
        if (reviewCount != null && (reviewCount < 0 || reviewCount > 10000000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El número de valoraciones de Temu no es válido.");
        }
        if (storeId != null && !storeId.matches("\\d{6,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El identificador de tienda Temu no es válido.");
        }
        if ((reviewCount != null || storeId != null || Boolean.TRUE.equals(sectionObserved))
                && (request.seller() == null || request.seller().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los datos comerciales de Temu requieren una tienda identificada.");
        }
        if (reviewCount != null && !Boolean.TRUE.equals(sectionObserved)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las valoraciones de Temu requieren inspección de la tienda.");
        }
        imported.setTemuSellerObservation(reviewCount, storeId, sectionObserved);

        // La valoración del PRODUCTO es independiente de la valoración de tienda.
        java.math.BigDecimal productRating = request.temuProductRating();
        Integer productReviews = request.temuProductReviewCount();
        if ((productRating != null || productReviews != null)
                && !"TEMU".equals(request.marketplace())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las opiniones de producto Temu no corresponden a este marketplace.");
        }
        if (productRating != null && (productRating.compareTo(java.math.BigDecimal.ZERO) <= 0
                || productRating.compareTo(new java.math.BigDecimal("5.0")) > 0
                || productRating.scale() > 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La calificación del producto Temu no es válida.");
        }
        if (productReviews != null && (productReviews < 0 || productReviews > 10000000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad de reseñas del producto Temu no es válida.");
        }
        // Admitimos observaciones parciales, pero no cuentan como cobertura completa.
        imported.setTemuProductReviews(productRating, productReviews);

        // Calificación y reseñas del PRODUCTO de Mercado Libre, distintas
        // de la reputación del vendedor y sólo informativas por ahora.
        java.math.BigDecimal mlRating = request.mlProductRating();
        Integer mlReviews = request.mlProductReviewCount();
        if ((mlRating != null || mlReviews != null)
                && !"MERCADO_LIBRE".equals(request.marketplace())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las opiniones de Mercado Libre no corresponden a este marketplace.");
        }
        if (mlRating != null && (mlRating.compareTo(java.math.BigDecimal.ZERO) <= 0
                || mlRating.compareTo(new java.math.BigDecimal("5.0")) > 0
                || mlRating.scale() > 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La calificación del producto Mercado Libre no es válida.");
        }
        if (mlReviews != null && (mlReviews <= 0 || mlReviews > 10000000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las opiniones del producto Mercado Libre no son válidas.");
        }
        // TRUE no significa cero opiniones: solo ausencia de calificación
        // pública observada en el producto principal.
        Boolean ratingUnavailable = request.mlProductRatingUnavailable();
        if (ratingUnavailable != null && !"MERCADO_LIBRE".equals(request.marketplace())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El indicador de calificación no corresponde a este marketplace.");
        }
        if (Boolean.TRUE.equals(ratingUnavailable)
                && (mlRating != null || mlReviews != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La ausencia observada contradice las métricas proporcionadas.");
        }
        imported.setMlProductReviews(mlRating, mlReviews);
        imported.setMlProductRatingUnavailable(
                Boolean.TRUE.equals(ratingUnavailable) ? true : null);
        // Datos comerciales observados en regiones separadas del DOM:
        // antigüedad de la TIENDA y ventas del PRODUCTO.
        String tenure = request.temuStoreTenureText();
        String sold = request.temuProductSoldText();
        String storeSold = request.temuStoreSoldText();
        if ((tenure != null || sold != null || storeSold != null)
                && !"TEMU".equals(request.marketplace())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los indicadores comerciales sólo corresponden a Temu.");
        }
        if ((tenure != null || storeSold != null)
                && (request.seller() == null || request.seller().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Los indicadores de la tienda requieren un vendedor identificado.");
        }
        if (tenure != null && (tenure.length() > 60
                || TemuCommercialEvidence.parseStoreTenure(tenure) == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La antigüedad observada de la tienda no es válida.");
        }
        if (sold != null && (sold.length() > 60
                || TemuCommercialEvidence.parseProductSales(sold) == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad de ventas observadas del producto no es válida.");
        }
        if (storeSold != null && (storeSold.length() > 60
                || TemuCommercialEvidence.parseProductSales(storeSold) == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La cantidad de ventas observadas de la tienda no es válida.");
        }
        imported.setTemuCommercialObservation(
                tenure == null ? null : tenure.trim(),
                sold == null ? null : sold.trim(),
                storeSold == null ? null : storeSold.trim());
        // Validar IDs antes de interpolarlos en comprobaciones de URL.
        validateSellerMetadata(request);

        imported.setSellerMetadata(
                request.sellerProfileUrl(),
                request.sellerBadge(),
                request.sellerSalesText());

        Integer reputationLevel = request.sellerReputationLevel();

        if (reputationLevel != null &&
                (reputationLevel < 1 || reputationLevel > 5)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nivel de reputación inválido.");
        }

        imported.setSellerReputationLevel(reputationLevel);

        // -------------------------------------
        // Año de incorporación a Facebook
        // -------------------------------------

        Integer facebookJoinYear = request.facebookJoinYear();

        if (facebookJoinYear != null) {

            if (!"FACEBOOK_MARKETPLACE".equals(request.marketplace())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El año de incorporación a Facebook no corresponde a este marketplace.");
            }

            int currentYear = Year.now().getValue();

            if (facebookJoinYear < 2004 ||
                    facebookJoinYear > currentYear) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El año de incorporación a Facebook no es válido.");
            }
        }

        imported.setFacebookJoinYear(facebookJoinYear);

        ExtensionImport saved = repository.save(imported);

        return new ExtensionImportResponse(
                saved.getId(),
                "STORED",
                saved.getMarketplace(),
                saved.getTitle(),
                saved.getSellerName(),
                saved.getCreatedAt());
    }

    private void validateSellerMetadata(
            ExtensionPreviewRequest request) {

        String profileUrl = request.sellerProfileUrl();
        String badge = request.sellerBadge();
        String salesText = request.sellerSalesText();

        if (profileUrl != null) {

            if (profileUrl.length() > 2048) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La URL del perfil es demasiado larga.");
            }

            try {
                URI uri = URI.create(profileUrl);

                String host = uri.getHost();
                String path = uri.getPath();

                boolean validHost = host != null &&
                        (host.equalsIgnoreCase("mercadolibre.com.ar") ||
                                host.equalsIgnoreCase("www.mercadolibre.com.ar"));
                boolean validPath = path != null && path.startsWith("/pagina/");
                if ("TEMU".equals(request.marketplace())) {
                    validHost = host != null && (host.equalsIgnoreCase("temu.com")
                            || host.toLowerCase(java.util.Locale.ROOT).endsWith(".temu.com"));
                    String wanted = request.temuStoreId();
                    validPath = wanted != null && path != null &&
                        (path.matches("(?i)^/.*-m-" + wanted + "\\.html/?$")
                        || path.matches("(?i)^/mall\\.html/?$")
                           && uri.getRawQuery() != null &&
                              java.util.Arrays.stream(uri.getRawQuery().split("&"))
                              .anyMatch(value -> value.equals("mall_id=" + wanted)));
                }
                if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                        !validHost || !validPath ||
                        uri.getUserInfo() != null) {

                    throw new IllegalArgumentException();
                }

            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La URL del perfil del vendedor no es válida.");
            }
        }

        if (badge != null && !(
                ("MERCADO_LIBRE".equals(request.marketplace()) && "MERCADOLIDER".equals(badge))
                || ("TEMU".equals(request.marketplace()) && "TEMU_STAR_SELLER".equals(badge)
                        && request.seller() != null && !request.seller().isBlank()))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Distinción de vendedor no reconocida.");
        }

        if (salesText != null &&
                salesText.length() > 120) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El indicador de ventas es demasiado largo.");
        }
    }

    private void validateLength(
            String value,
            int maximum,
            String field) {

        if (value != null && value.length() > maximum) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El campo " + field +
                            " supera la longitud máxima permitida.");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private void validateFacebookUrl(String rawUrl) {

        if (rawUrl == null || rawUrl.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La URL de Facebook no puede estar vacía.");
        }

        try {
            URI uri = URI.create(rawUrl.trim());

            String host = uri.getHost();
            String path = uri.getPath();

            boolean validHost = host != null &&
                    (host.equalsIgnoreCase("facebook.com") ||
                            host.toLowerCase(java.util.Locale.ROOT)
                                    .endsWith(".facebook.com"));

            boolean validPath = path != null &&
                    path.matches(
                            "^/marketplace/item/\\d{6,30}/?$");

            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    !validHost ||
                    !validPath ||
                    uri.getUserInfo() != null) {

                throw new IllegalArgumentException();
            }

        } catch (IllegalArgumentException exception) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La URL no corresponde a una publicación válida de Facebook Marketplace.");
        }
    }
}