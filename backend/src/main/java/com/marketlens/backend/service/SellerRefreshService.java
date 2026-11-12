package com.marketlens.backend.service;

import com.marketlens.backend.dto.MercadoLibreItemResponse;
import com.marketlens.backend.dto.MercadoLibreUserProductResponse;
import com.marketlens.backend.dto.MercadoLibreUserResponse;
import com.marketlens.backend.dto.SellerRefreshResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SellerRefreshService {

    private final PublicationRepository publicationRepository;
    private final SellerRepository sellerRepository;
    private final MercadoLibreApiService mercadoLibreApiService;

    public SellerRefreshService(
            PublicationRepository publicationRepository,
            SellerRepository sellerRepository,
            MercadoLibreApiService mercadoLibreApiService) {
        this.publicationRepository = publicationRepository;
        this.sellerRepository = sellerRepository;
        this.mercadoLibreApiService = mercadoLibreApiService;
    }

    @Transactional
    public SellerRefreshResponse refresh(Long publicationId, String username) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId));

        if (publication.getMarketplace() != Marketplace.MERCADO_LIBRE) {
            return new SellerRefreshResponse("NOT_SUPPORTED", null, null);
        }

        Seller previousSeller = publication.getSeller();
        if (previousSeller == null ||
                previousSeller.getMarketplace() != Marketplace.MERCADO_LIBRE) {
            return new SellerRefreshResponse("UNVERIFIED", null, null);
        }

        // Identificar al propietario desde la referencia de Mercado Libre.
        // Nunca usar el nombre visible ni un sellerId enviado por el navegador
        // como prueba suficiente de identidad.
        Long authoritativeSellerId = resolveOwnerFromListing(publication, username);
        if (authoritativeSellerId == null || authoritativeSellerId <= 0) {
            return new SellerRefreshResponse("UNVERIFIED", null, null);
        }

        // Si antes se verificó un vendedor diferente, no sustituirlo.
        Long previousExternalId = previousSeller.getExternalSellerId();
        if (previousExternalId != null &&
                !Objects.equals(previousExternalId, authoritativeSellerId)) {
            return new SellerRefreshResponse("UNVERIFIED", null, null);
        }

        MercadoLibreUserResponse user = mercadoLibreApiService.getPublicUser(
                username, authoritativeSellerId);
        if (!Objects.equals(user.id(), authoritativeSellerId)) {
            return new SellerRefreshResponse("UNVERIFIED", null, null);
        }

        Seller verifiedSeller = sellerRepository
                .findByMarketplaceAndExternalSellerId(
                        Marketplace.MERCADO_LIBRE, authoritativeSellerId)
                .orElse(null);

        if (verifiedSeller == null) {
            // El registro anterior podría estar asociado a otros anuncios.
            // Evitamos convertirlo en un vendedor verificado sin saberlo.
            // Creamos uno nuevo y reasociamos SOLO esta publicación.
            verifiedSeller = new Seller();
            verifiedSeller.setMarketplace(Marketplace.MERCADO_LIBRE);
            verifiedSeller.setExternalSellerId(authoritativeSellerId);
            verifiedSeller.setName(
                    user.nickname() != null && !user.nickname().isBlank()
                            ? user.nickname()
                            : previousSeller.getName());
            verifiedSeller.setReputation(null);
            verifiedSeller.setAccountAgeDays(null);
            verifiedSeller = sellerRepository.save(verifiedSeller);
        }

        if (!Objects.equals(previousSeller.getId(), verifiedSeller.getId())) {
            publication.setSeller(verifiedSeller);
            publicationRepository.save(publication);
        }

        Integer reputationLevel = parseReputationLevel(user.reputationLevel());
        if (reputationLevel != null) {
            verifiedSeller.setReputationLevel(reputationLevel);
        }

        // Solo aceptar ventas históricas, nunca asumir cero ante dato ausente.
        Integer sales = user.completedSales();
        if (sales == null || sales < 0 ||
                !"historic".equalsIgnoreCase(user.salesPeriod())) {
            sellerRepository.save(verifiedSeller);
            return new SellerRefreshResponse(
                    "NO_HISTORIC_DATA",
                    verifiedSeller.getCompletedSales(),
                    verifiedSeller.getSalesObservedAt());
        }

        LocalDateTime now = LocalDateTime.now();
        if (verifiedSeller.getSalesObservedAt() != null &&
                verifiedSeller.getSalesObservedAt().isAfter(now)) {
            return new SellerRefreshResponse(
                    "UNCHANGED",
                    verifiedSeller.getCompletedSales(),
                    verifiedSeller.getSalesObservedAt());
        }

        verifiedSeller.setCompletedSales(sales);
        verifiedSeller.setSalesObservedAt(now);
        sellerRepository.save(verifiedSeller);
        return new SellerRefreshResponse("UPDATED", sales, now);
    }

    private Long resolveOwnerFromListing(Publication publication, String username) {
        String id = publication.getExternalId();
        String type = publication.getReferenceType();
        if (id == null || type == null) return null;

        // UP: el user_id identifica al vendedor propietario del User Product.
        if ("USER_PRODUCT".equals(type) && id.matches("MLAU\\d{6,}")) {
            MercadoLibreUserProductResponse product =
                    mercadoLibreApiService.getUserProduct(username, id);
            return id.equals(product.id()) ? product.sellerId() : null;
        }

        // Listing convencional: seller_id procede del detalle oficial del ítem.
        if ("LISTING".equals(type) && id.matches("MLA\\d{6,}")) {
            MercadoLibreItemResponse item = mercadoLibreApiService.getItem(username, id);
            return id.equals(item.id()) ? item.sellerId() : null;
        }

        return null;
    }

    private Integer parseReputationLevel(String levelId) {
        if (levelId == null) return null;
        return switch (levelId) {
            case "1_red" -> 1;
            case "2_orange" -> 2;
            case "3_yellow" -> 3;
            case "4_light_green" -> 4;
            case "5_green" -> 5;
            default -> null;
        };
    }
}
