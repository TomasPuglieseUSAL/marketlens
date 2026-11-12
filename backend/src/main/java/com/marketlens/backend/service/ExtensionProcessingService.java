package com.marketlens.backend.service;

import com.marketlens.backend.dto.ExtensionNormalizationResponse;
import com.marketlens.backend.dto.ExtensionProcessingResponse;

import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;

import com.marketlens.backend.repository.ExtensionImportRepository;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.SellerRepository;
import com.marketlens.backend.dto.MercadoLibreUserResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ExtensionProcessingService {

        private final ExtensionImportRepository importRepository;
        private final ExtensionNormalizationService normalizationService;
        private final PublicationRepository publicationRepository;
        private final SellerRepository sellerRepository;
        private final MercadoLibreApiService mercadoLibreApiService;

        public ExtensionProcessingService(
                        ExtensionImportRepository importRepository,
                        ExtensionNormalizationService normalizationService,
                        PublicationRepository publicationRepository,
                        SellerRepository sellerRepository,
                        MercadoLibreApiService mercadoLibreApiService) {

                this.importRepository = importRepository;
                this.normalizationService = normalizationService;
                this.publicationRepository = publicationRepository;
                this.sellerRepository = sellerRepository;
                this.mercadoLibreApiService = mercadoLibreApiService;
        }

        @Transactional
        public ExtensionProcessingResponse process(
                        Long importId,
                        String username) {

                // 1. Recuperar y bloquear la importación.
                ExtensionImport imported = importRepository
                                .findOwnedForProcessing(importId, username)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Importación no encontrada."));

                // 2. Evitar volver a procesar el mismo registro.
                if (imported.getProcessedPublication() != null) {

                        return new ExtensionProcessingResponse(
                                        imported.getId(),
                                        imported.getProcessedPublication().getId(),
                                        "ALREADY_PROCESSED",
                                        false);
                }

                // 3. Normalizar los datos.
                ExtensionNormalizationResponse normalized = normalizationService.normalize(importId, username);

                validateNormalizedData(normalized, imported);

                Marketplace marketplace = Marketplace.valueOf(
                                normalized.marketplace());

                VerifiedSeller verified = marketplace == Marketplace.MERCADO_LIBRE
                                ? verifySeller(imported, normalized, username)
                                : null;

                // 4. Buscar una publicación existente.
                Optional<Publication> existing = publicationRepository.findByMarketplaceAndExternalId(
                                marketplace,
                                normalized.referenceId());

                Publication publication;
                String status;
                boolean created;

                if (existing.isPresent()) {

                        publication = existing.get();

                        String existingType = publication.getReferenceType();
                        String incomingType = normalized.referenceType();

                        if (existingType != null &&
                                        !existingType.equals(incomingType)) {

                                throw new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "La publicación existente tiene otro tipo de referencia.");
                        }

                        if (marketplace == Marketplace.FACEBOOK_MARKETPLACE) {

                                // Facebook: el identificador externo identifica el anuncio,
                                // pero el vendedor no dispone de una identidad verificada
                                // equivalente a la de Mercado Libre.

                                Seller existingSeller = publication.getSeller();

                                String existingName = existingSeller == null
                                                ? null
                                                : existingSeller.getName();

                                String incomingName = normalized.sellerName();

                                if (existingName == null ||
                                                incomingName == null ||
                                                !existingName.trim().equalsIgnoreCase(
                                                                incomingName.trim())) {

                                        throw new ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "El anuncio de Facebook existe, pero los " +
                                                                        "datos del vendedor no coinciden.");
                                }

                                // No reutilizar silenciosamente un precio diferente.
                                if (publication.getPrice().compareTo(
                                                normalized.price()) != 0) {

                                        throw new ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "El anuncio de Facebook existe con otro precio. " +
                                                                        "Se necesita actualizar la observación.");
                                }

                        } else {

                                // Mercado Libre: conservar la verificación original.
                                Long existingSellerId = publication.getSeller().getExternalSellerId();

                                if (verified == null ||
                                                !Objects.equals(
                                                                existingSellerId,
                                                                verified.id())) {

                                        throw new ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "No se pudo confirmar que la publicación " +
                                                                        "pertenezca al mismo vendedor.");
                                }

                                if (publication.getPrice().compareTo(
                                                normalized.price()) != 0) {

                                        throw new ResponseStatusException(
                                                        HttpStatus.CONFLICT,
                                                        "La referencia ya existe con otro precio. " +
                                                                        "Se requiere registrar una nueva observación.");
                                }
                        }

                        if (marketplace == Marketplace.MERCADO_LIBRE && verified != null) {
                                updateVerifiedSeller(publication.getSeller(), verified);
                                sellerRepository.save(publication.getSeller());
                        }

                        status = "REUSED";
                        created = false;

                } else {

                        // No existe la publicación.
                        // Resolver el vendedor mediante su identidad externa.
                        Seller savedSeller;

                        if (marketplace == Marketplace.MERCADO_LIBRE) {

                                savedSeller = resolveSeller(normalized, verified);

                        } else {

                                Seller seller = new Seller();

                                seller.setName(normalized.sellerName());
                                seller.setMarketplace(marketplace);

                                seller.setExternalSellerId(null);
                                seller.setReputation(null);
                                seller.setReputationLevel(null);
                                seller.setAccountAgeDays(null);

                                savedSeller = sellerRepository.save(seller);
                        }

                        // Crear la publicación.
                        publication = new Publication();

                        publication.setTitle(imported.getTitle());
                        publication.setDescription(imported.getDescription());

                        publication.setPrice(normalized.price());
                        publication.setMarketplace(marketplace);

                        publication.setExternalId(normalized.referenceId());
                        publication.setReferenceType(normalized.referenceType());
                        publication.setOriginalUrl(imported.getOriginalUrl());

                        publication.setSeller(savedSeller);

                        // Conservar el año de incorporación observado en Facebook.
                        // No representa una antigüedad exacta en días.
                        if (marketplace == Marketplace.FACEBOOK_MARKETPLACE) {
                                publication.setFacebookJoinYear(
                                                imported.getFacebookJoinYear());
                        }

                        // Guardar la publicación en PostgreSQL.
                        publication = publicationRepository.save(publication);

                        status = "CREATED";
                        created = true;
                }

                // 7. Asociar la importación con la publicación.
                imported.markProcessed(publication);

                importRepository.save(imported);

                return new ExtensionProcessingResponse(
                                imported.getId(),
                                publication.getId(),
                                status,
                                created);
        }

        @SuppressWarnings("deprecation")
        private void validateNormalizedData(
                        ExtensionNormalizationResponse normalized,
                        ExtensionImport imported) {

                if (normalized.title() == null ||
                                normalized.title().isBlank()) {

                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "La publicación no tiene un título válido.");
                }

                if (normalized.title().length() > 255) {

                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "El título supera los 255 caracteres permitidos.");
                }

                if (imported.getOriginalUrl() == null ||
                                imported.getOriginalUrl().length() > 1000) {

                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "La URL supera la longitud permitida.");
                }

                if (normalized.referenceId() != null &&
                                normalized.referenceId().length() > 255) {

                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "El identificador externo es demasiado largo.");
                }

                String marketplace = normalized.marketplace();
                String referenceType = normalized.referenceType();

                boolean supported = ("MERCADO_LIBRE".equals(marketplace) &&
                                ("LISTING".equals(referenceType) ||
                                                "USER_PRODUCT".equals(referenceType)))
                                ||
                                ("FACEBOOK_MARKETPLACE".equals(marketplace) &&
                                                "LISTING".equals(referenceType));

                if (!supported) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Marketplace o tipo de referencia no soportado.");
                }

                if (normalized.referenceId() == null ||
                                normalized.referenceId().isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "La publicación no tiene un identificador válido.");
                }

                if (!"ARS".equals(normalized.currency())) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Por ahora solamente se admiten precios en ARS.");
                }

                BigDecimal price = normalized.price();

                if (price == null || price.signum() <= 0) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "No se pudo normalizar un precio válido.");
                }

                try {
                        BigDecimal normalizedPrice = price.setScale(
                                        2,
                                        RoundingMode.UNNECESSARY);

                        BigDecimal maximum = new BigDecimal("10000000000000");

                        if (normalizedPrice.compareTo(maximum) >= 0) {
                                throw new ResponseStatusException(
                                                HttpStatus.UNPROCESSABLE_ENTITY,
                                                "El precio supera el máximo permitido.");
                        }

                } catch (ArithmeticException exception) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "El precio tiene demasiados decimales.");
                }

                if (normalized.sellerName() == null ||
                                normalized.sellerName().isBlank()) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "No se identificó al vendedor.");
                }

                boolean conflictingId = normalized.warnings()
                                .stream()
                                .anyMatch(warning -> warning.contains("no coincide con el de la URL"));

                if (conflictingId) {
                        throw new ResponseStatusException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "Hay una inconsistencia entre los identificadores.");
                }
        }

        private record VerifiedSeller(
                        Long id,
                        Integer reputationLevel) {
        }

        private VerifiedSeller verifySeller(
                        ExtensionImport imported,
                        ExtensionNormalizationResponse normalized,
                        String username) {

                Long sellerId = imported.getExternalSellerId();

                if (sellerId == null || sellerId <= 0) {
                        return null;
                }

                MercadoLibreUserResponse user;

                try {
                        user = mercadoLibreApiService.getPublicUser(
                                        username,
                                        sellerId);
                } catch (ResponseStatusException exception) {
                        // Si la API no permite la consulta, conservamos
                        // la importación sin confirmar la identidad.
                        return null;
                }

                if (!Objects.equals(user.id(), sellerId)) {
                        return null;
                }

                String expectedName = normalized.sellerName();
                String returnedNickname = user.nickname();

                if (expectedName == null ||
                                returnedNickname == null ||
                                !expectedName.trim().equalsIgnoreCase(
                                                returnedNickname.trim())) {

                        // Un nombre comercial puede ser diferente del
                        // nickname. No confirmamos esa asociación
                        // automáticamente.
                        return null;
                }

                Integer reputationLevel = parseReputationLevel(user.reputationLevel());

                return new VerifiedSeller(
                                sellerId,
                                reputationLevel);
        }

        private Integer parseReputationLevel(String levelId) {

                if (levelId == null) {
                        return null;
                }

                return switch (levelId) {
                        case "1_red" -> 1;
                        case "2_orange" -> 2;
                        case "3_yellow" -> 3;
                        case "4_light_green" -> 4;
                        case "5_green" -> 5;
                        default -> null;
                };
        }

        // Solo se invoca con identidad corroborada por Mercado Libre.
        private void updateVerifiedSeller(Seller seller, VerifiedSeller verified) {
                if (verified.reputationLevel() != null) {
                        seller.setReputationLevel(verified.reputationLevel());
                }
        }

        private Seller resolveSeller(
                        ExtensionNormalizationResponse normalized,
                        VerifiedSeller verified) {

                Seller seller;

                if (verified != null) {

                        seller = sellerRepository
                                        .findByMarketplaceAndExternalSellerId(
                                                        Marketplace.MERCADO_LIBRE,
                                                        verified.id())
                                        .orElseGet(Seller::new);

                } else {
                        // No asociar un ID externo sin verificar.
                        seller = new Seller();
                }

                if (seller.getId() == null) {

                        seller.setName(normalized.sellerName());
                        seller.setMarketplace(Marketplace.MERCADO_LIBRE);

                        seller.setReputation(null);
                        seller.setAccountAgeDays(null);

                        if (verified != null) {
                                seller.setExternalSellerId(verified.id());
                        }
                }

                if (verified != null) {
                        updateVerifiedSeller(seller, verified);
                }

                return sellerRepository.save(seller);
        }
}