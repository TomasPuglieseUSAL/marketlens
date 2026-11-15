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

                        if (marketplace == Marketplace.TEMU) {
                                // goods_id no confirma la identidad de una tienda.
                                // Conservar datos previos salvo información compatible con la observación.
                                if (publication.getPrice().compareTo(normalized.price()) != 0) {
                                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                "El precio del producto Temu cambió; se requiere una nueva observación.");
                                }
                                String incomingName = normalized.sellerName();
                                Seller previous = publication.getSeller();
                                if (incomingName != null && !incomingName.isBlank()) {
                                        if (previous == null) {
                                                publication.setSeller(createObservedTemuSeller(incomingName));
                                        } else if (previous.getName() == null ||
                                                !previous.getName().equalsIgnoreCase(incomingName.trim())) {
                                                // Corrección acotada para registros antiguos en los que
                                                // un texto de puntuación del PRODUCTO se tomó por vendedor.
                                                // No modificar un vendedor existente con nombre real.
                                                if (previous.getName() != null &&
                                                        previous.getName().matches(
                                                            "(?i)^\\s*[0-5](?:[.,]\\d)?\\s+out\\s+of\\s+(?:five|5)\\s+stars?\\s*$")) {
                                                        publication.setSeller(createObservedTemuSeller(incomingName));
                                                } else {
                                                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                                                "La tienda observada de Temu no coincide con la registrada.");
                                                }
                                        }
                                }
                                if ("TEMU_STAR_SELLER".equals(imported.getSellerBadge())
                                        && incomingName != null && !incomingName.isBlank()) {
                                        publication.setSellerBadge("TEMU_STAR_SELLER");
                                }
                                if (publication.getSeller() != null &&
                                        incomingName != null &&
                                        publication.getSeller().getName().equalsIgnoreCase(incomingName.trim())) {
                                        applyTemuSellerEvidence(publication, imported);
                                }
                                // Una extracción más reciente puede incluir detalles que faltaban
                                // cuando se creó esta publicación (p. ej., #goodsDetail de Temu).
                                // Completar solo una descripción vacía, sin sobrescribir la anterior.
                                fillMissingTemuDescription(publication, imported);
                                applyTemuProductEvidence(publication, imported);
                                applyTemuCommercialEvidence(publication, imported);
                        } else if (marketplace == Marketplace.FACEBOOK_MARKETPLACE) {

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
                        } else if (marketplace == Marketplace.TEMU) {
                                // Nombre de tienda observado en esta publicación; no identidad verificada.
                                savedSeller = normalized.sellerName() == null
                                        || normalized.sellerName().isBlank()
                                        ? null : createObservedTemuSeller(normalized.sellerName());
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
                        if (marketplace == Marketplace.TEMU) {
                                applyTemuProductEvidence(publication, imported);
                        }

                        publication.setPrice(normalized.price());
                        publication.setMarketplace(marketplace);

                        publication.setExternalId(normalized.referenceId());
                        publication.setReferenceType(normalized.referenceType());
                        publication.setOriginalUrl(imported.getOriginalUrl());

                        publication.setSeller(savedSeller);
                        if (marketplace == Marketplace.TEMU && savedSeller != null) {
                                if ("TEMU_STAR_SELLER".equals(imported.getSellerBadge())) {
                                        publication.setSellerBadge("TEMU_STAR_SELLER");
                                }
                                applyTemuSellerEvidence(publication, imported);
                                applyTemuCommercialEvidence(publication, imported);
                        }

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
                                                "LISTING".equals(referenceType))
                                ||
                                ("TEMU".equals(marketplace) &&
                                                "PRODUCT".equals(referenceType));

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

                if (!"TEMU".equals(marketplace) &&
                                (normalized.sellerName() == null ||
                                normalized.sellerName().isBlank())) {
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

        static void fillMissingTemuDescription(Publication publication, ExtensionImport imported) {
                if (publication == null || imported == null) return;
                String previous = publication.getDescription();
                String incoming = imported.getDescription();
                if ((previous == null || previous.isBlank())
                        && incoming != null && !incoming.isBlank()) {
                        publication.setDescription(incoming);
                }
        }

        static void applyTemuProductEvidence(Publication publication, ExtensionImport imported) {
                if (publication == null || imported == null) return;
                // Conservar la observación anterior si la nueva extracción
                // no trae ambos valores; la fecha antigua vencerá igualmente.
                if (TemuProductReviewEvidence.hasMetrics(
                        imported.getTemuProductRating(),
                        imported.getTemuProductReviewCount())) {
                        publication.setTemuProductRating(imported.getTemuProductRating());
                        publication.setTemuProductReviewCount(imported.getTemuProductReviewCount());
                        publication.setTemuProductObservedAt(java.time.LocalDateTime.now());
                }
        }

        static void applyTemuCommercialEvidence(Publication publication, ExtensionImport imported) {
                if (publication == null || imported == null
                        || publication.getMarketplace() != Marketplace.TEMU) return;
                // El texto de ventas siempre pertenece al producto y puede
                // actualizarse incluso si no hubo nuevos datos de la tienda.
                String sold = imported.getTemuProductSoldText();
                if (TemuCommercialEvidence.parseProductSales(sold) != null) {
                        publication.setTemuProductSoldText(sold);
                        publication.setTemuProductSalesObservedAt(java.time.LocalDateTime.now());
                }

                // Un goods_id no acredita la identidad de la tienda: exigir
                // que el nombre observado coincida con el vendedor asociado.
                String name = imported.getSellerName();
                if (publication.getSeller() == null || name == null
                        || publication.getSeller().getName() == null
                        || !publication.getSeller().getName().equalsIgnoreCase(name.trim())) return;
                String tenure = imported.getTemuStoreTenureText();
                if (TemuCommercialEvidence.parseStoreTenure(tenure) != null) {
                        publication.setTemuStoreTenureText(tenure);
                        publication.setTemuStoreTenureObservedAt(java.time.LocalDateTime.now());
                }
        }

        private void applyTemuSellerEvidence(Publication publication, ExtensionImport imported) {
                String incomingStoreId = imported.getTemuStoreId();
                String previousId = publication.getTemuStoreId();
                if (previousId != null && incomingStoreId != null &&
                        !previousId.equals(incomingStoreId)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "La tienda de Temu no coincide con el identificador anteriormente registrado.");
                }
                if (incomingStoreId != null) publication.setTemuStoreId(incomingStoreId);

                if (Boolean.TRUE.equals(imported.getTemuSellerSectionObserved())
                        || imported.getTemuSellerRating() != null
                        || imported.getTemuSellerReviewCount() != null) {
                        publication.setTemuSellerRating(imported.getTemuSellerRating());
                        publication.setTemuSellerReviewCount(imported.getTemuSellerReviewCount());
                        publication.setTemuSellerSectionObserved(imported.getTemuSellerSectionObserved());
                        publication.setTemuSellerObservedAt(java.time.LocalDateTime.now());
                }
        }

        private Seller createObservedTemuSeller(String name) {
                Seller seller = new Seller();
                seller.setName(name.trim());
                seller.setMarketplace(Marketplace.TEMU);
                // Sin identificador externo ni reputación comprobada.
                seller.setExternalSellerId(null);
                seller.setReputation(null);
                seller.setReputationLevel(null);
                seller.setAccountAgeDays(null);
                return sellerRepository.save(seller);
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