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
                !"FACEBOOK_MARKETPLACE".equals(request.marketplace())) {

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

                if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                        !validHost ||
                        path == null ||
                        !path.startsWith("/pagina/") ||
                        uri.getUserInfo() != null) {

                    throw new IllegalArgumentException();
                }

            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "La URL del perfil del vendedor no es válida.");
            }
        }

        if (badge != null &&
                !badge.equals("MERCADOLIDER")) {

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