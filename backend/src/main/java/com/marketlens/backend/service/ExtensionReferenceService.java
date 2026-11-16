package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionNormalizationResponse;
import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.MarketplaceReference;
import com.marketlens.backend.dto.ExtensionReferenceResponse;

import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.repository.MarketplaceReferenceRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExtensionReferenceService {

    private final ExtensionImportRepository importRepository;
    private final MarketplaceReferenceRepository referenceRepository;
    private final ExtensionNormalizationService normalizationService;

    public ExtensionReferenceService(
            ExtensionImportRepository importRepository,
            MarketplaceReferenceRepository referenceRepository,
            ExtensionNormalizationService normalizationService) {

        this.importRepository = importRepository;
        this.referenceRepository = referenceRepository;
        this.normalizationService = normalizationService;
    }

    @Transactional
    public ExtensionReferenceResponse register(
            Long importId,
            String username) {

        // Recuperar y bloquear la importación.
        ExtensionImport imported = importRepository
                .findOwnedForProcessing(importId, username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Importación no encontrada."));

        // Si ya tiene referencia, reutilizarla.
        if (imported.getMarketplaceReference() != null) {

            MarketplaceReference reference = imported.getMarketplaceReference();

            return toResponse(
                    imported,
                    reference,
                    "ALREADY_LINKED");
        }

        ExtensionNormalizationResponse normalized = normalizationService.normalize(importId, username);

        validate(normalized);

        Marketplace marketplace = Marketplace.valueOf(
                normalized.marketplace());

        // Buscar referencia previamente identificada.
        var existing = referenceRepository
                .findByMarketplaceAndReferenceTypeAndExternalId(
                        marketplace,
                        normalized.referenceType(),
                        normalized.referenceId());

        MarketplaceReference reference;
        String status;

        if (existing.isPresent()) {

            reference = existing.get();
            status = "REUSED";

        } else {

            reference = new MarketplaceReference(
                    marketplace,
                    normalized.referenceType(),
                    normalized.referenceId());

            reference = referenceRepository.save(reference);
            status = "CREATED";
        }

        imported.setMarketplaceReference(reference);

        importRepository.save(imported);

        return toResponse(imported, reference, status);
    }

    private void validate(
            ExtensionNormalizationResponse normalized) {

        String marketplace = normalized.marketplace();
        String type = normalized.referenceType();
        String id = normalized.referenceId();

        // 1. Validar que exista un identificador.
        if (id == null || id.isBlank() || id.length() > 120) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Identificador externo inválido.");
        }

        // Un goods_id identifica un producto Temu, no a su vendedor.
        if ("TEMU".equals(marketplace)) {
            if (!"PRODUCT".equals(type) || !id.matches("\\d{6,30}")) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tipo o identificador Temu inválido.");
            }
        } else if ("FACEBOOK_MARKETPLACE".equals(marketplace)) {

            // Facebook utiliza referencias LISTING con ID numérico.
            if (!"LISTING".equals(type)) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tipo de referencia no soportado para Facebook Marketplace.");
            }

            if (!id.matches("\\d{6,30}")) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Identificador de Facebook Marketplace inválido.");
            }

            // 3. Validaciones específicas de Mercado Libre.
        } else if ("MERCADO_LIBRE".equals(marketplace)) {

            if (!"LISTING".equals(type)
                    && !"CATALOG_PRODUCT".equals(type)
                    && !"USER_PRODUCT".equals(type)) {

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tipo de referencia no soportado para Mercado Libre.");
            }

            if ("USER_PRODUCT".equals(type)) {

                if (!id.matches("MLAU\\d{6,}")) {
                    throw new ResponseStatusException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "Identificador de User Product inválido.");
                }

            } else if (!id.matches("ML[A-Z]\\d{6,}")) {

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Identificador externo de Mercado Libre inválido.");
            }

            // 4. Rechazar cualquier otra plataforma.
        } else {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Marketplace no soportado.");
        }

        // 5. Conservar la validación de inconsistencias.
        boolean conflictingId = normalized.warnings()
                .stream()
                .anyMatch(warning -> warning.contains("no coincide con el de la URL"));

        if (conflictingId) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Identificador inconsistente con la URL.");
        }
    }

    private ExtensionReferenceResponse toResponse(
            ExtensionImport imported,
            MarketplaceReference reference,
            String status) {

        return new ExtensionReferenceResponse(
                imported.getId(),
                reference.getId(),
                reference.getReferenceType(),
                reference.getExternalId(),
                status);
    }
}