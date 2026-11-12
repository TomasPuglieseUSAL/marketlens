package com.marketlens.backend.controller;

import com.marketlens.backend.dto.ExtensionPreviewRequest;
import com.marketlens.backend.dto.ExtensionPreviewResponse;
import com.marketlens.backend.dto.ExtensionImportResponse;
import com.marketlens.backend.dto.ExtensionNormalizationResponse;
import com.marketlens.backend.service.ExtensionImportService;
import com.marketlens.backend.service.ExtensionNormalizationService;
import com.marketlens.backend.dto.ExtensionProcessingResponse;
import com.marketlens.backend.service.ExtensionProcessingService;
import com.marketlens.backend.dto.ExtensionReferenceResponse;
import com.marketlens.backend.service.ExtensionReferenceService;
import com.marketlens.backend.dto.RiskCoverageResponse;
import com.marketlens.backend.service.ExtensionRiskCoverageService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/extension")
public class ExtensionController {
    private final ExtensionImportService importService;
    private final ExtensionNormalizationService normalizationService;
    private final ExtensionProcessingService processingService;
    private final ExtensionReferenceService referenceService;
    private final ExtensionRiskCoverageService coverageService;

    public ExtensionController(
            ExtensionImportService importService,
            ExtensionNormalizationService normalizationService,
            ExtensionProcessingService processingService,
            ExtensionReferenceService referenceService,
            ExtensionRiskCoverageService coverageService) {

        this.importService = importService;
        this.normalizationService = normalizationService;
        this.processingService = processingService;
        this.referenceService = referenceService;
        this.coverageService = coverageService;
    }

    @PostMapping("/preview")
    public ExtensionPreviewResponse preview(
            @RequestBody ExtensionPreviewRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se recibieron datos.");
        }

        String marketplace = request.marketplace();

        // 1. Validar marketplace.
        boolean supported = "MERCADO_LIBRE".equals(marketplace) ||
                "FACEBOOK_MARKETPLACE".equals(marketplace);

        if (!supported) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Marketplace no soportado.");
        }

        // 2. Validar título.
        if (isBlank(request.title())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "El título es obligatorio.");
        }

        // 3. Validar URL según marketplace.
        if (isBlank(request.url())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La URL es obligatoria.");
        }

        boolean validUrl = switch (marketplace) {
            case "MERCADO_LIBRE" ->
                isMercadoLibreUrl(request.url());

            case "FACEBOOK_MARKETPLACE" ->
                isFacebookMarketplaceUrl(request.url());

            default -> false;
        };

        if (!validUrl) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La URL no corresponde al marketplace indicado.");
        }

        Integer joinYear = request.facebookJoinYear();

        // 4.5 Validar año de incorporación a Facebook Marketplace
        if (joinYear != null) {

            if (!"FACEBOOK_MARKETPLACE".equals(request.marketplace())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "El año de incorporación solo se admite para Facebook Marketplace.");
            }

            int currentYear = java.time.Year.now().getValue();

            if (joinYear < 2004 || joinYear > currentYear) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Año de incorporación a Facebook inválido.");
            }
        }

        // 4. Identificar datos faltantes.
        List<String> missingFields = new ArrayList<>();

        if (isBlank(request.price())) {
            missingFields.add("price");
        }

        if (isBlank(request.currency())) {
            missingFields.add("currency");
        }

        if (isBlank(request.seller())) {
            missingFields.add("seller");
        }

        if (isBlank(request.externalId())) {
            missingFields.add("externalId");
        }

        if (isBlank(request.description())) {
            missingFields.add("description");
        }

        return new ExtensionPreviewResponse(
                true,
                request,
                missingFields);
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public ExtensionImportResponse importPublication(
            @RequestBody ExtensionPreviewRequest request,
            Authentication authentication) {

        // Reutilizamos las validaciones ya implementadas
        // para el endpoint de vista previa.
        preview(request);

        if (authentication == null ||
                !authentication.isAuthenticated()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Se requiere autenticación.");
        }

        return importService.importPublication(
                request,
                authentication.getName());
    }

    @GetMapping("/imports/{id}/normalized")
    public ExtensionNormalizationResponse getNormalizedImport(
            @PathVariable Long id,
            Authentication authentication) {

        return normalizationService.normalize(
                id,
                authentication.getName());
    }

    @PostMapping("/imports/{id}/process")
    public ExtensionProcessingResponse processImport(
            @PathVariable Long id,
            Authentication authentication) {

        return processingService.process(
                id,
                authentication.getName());
    }

    @PostMapping("/imports/{id}/reference")
    public ExtensionReferenceResponse registerReference(
            @PathVariable Long id,
            Authentication authentication) {

        return referenceService.register(
                id,
                authentication.getName());
    }

    @GetMapping("/imports/{id}/coverage")
    public RiskCoverageResponse getImportCoverage(
            @PathVariable Long id,
            Authentication authentication) {

        return coverageService.getCoverage(
                id,
                authentication.getName());
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isMercadoLibreUrl(String value) {
        try {
            URI uri = URI.create(value);

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            if (uri.getUserInfo() != null) {
                return false;
            }

            String host = uri.getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);

            return matchesDomain(host, "mercadolibre.com.ar")
                    || matchesDomain(host, "mercadolibre.com")
                    || matchesDomain(host, "mercadolivre.com.br");

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isFacebookMarketplaceUrl(String value) {
        try {
            URI uri = URI.create(value);

            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }

            if (uri.getUserInfo() != null) {
                return false;
            }

            String host = uri.getHost();

            if (host == null) {
                return false;
            }

            host = host.toLowerCase(Locale.ROOT);

            boolean validHost = host.equals("facebook.com") ||
                    host.equals("www.facebook.com") ||
                    host.equals("m.facebook.com");

            if (!validHost) {
                return false;
            }

            String path = uri.getPath();

            return path != null &&
                    path.matches(
                            "^/marketplace/item/\\d{6,30}/?$");

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean matchesDomain(String host, String domain) {
        return host.equals(domain)
                || host.endsWith("." + domain);
    }
}