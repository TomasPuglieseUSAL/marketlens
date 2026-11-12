package com.marketlens.backend.service;

import com.marketlens.backend.dto.SellerSalesObservationRequest;
import com.marketlens.backend.dto.SellerSalesObservationResponse;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.SellerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SellerSalesObservationService {

    private final PublicationRepository publicationRepository;
    private final SellerRepository sellerRepository;

    public SellerSalesObservationService(
            PublicationRepository publicationRepository,
            SellerRepository sellerRepository) {
        this.publicationRepository = publicationRepository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public SellerSalesObservationResponse observe(
            Long publicationId, SellerSalesObservationRequest request) {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Falta la observación del vendedor.");
        }
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId));

        // Conservamos la vinculación a la publicación exacta.
        if (!"MERCADO_LIBRE".equals(request.marketplace()) ||
                publication.getMarketplace() != Marketplace.MERCADO_LIBRE ||
                request.externalId() == null ||
                publication.getExternalId() == null ||
                !publication.getExternalId().equalsIgnoreCase(request.externalId().strip())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La observación no corresponde a la publicación solicitada.");
        }

        Seller seller = publication.getSeller();
        if (seller == null) {
            return new SellerSalesObservationResponse("NO_SELLER", null, null);
        }

        // No permitir que una observación actualice un vendedor con otro nombre
        // asociado a la publicación. Los datos reportados por DOM no certifican identidad.
        if (!sameDisplayedName(seller.getName(), request.sellerName())) {
            return new SellerSalesObservationResponse("SELLER_MISMATCH",
                    seller.getCompletedSales(), seller.getSalesObservedAt());
        }

        LocalDateTime now = LocalDateTime.now();
        boolean activityUpdated = false;

        // Los productos son un dato diferente de las ventas: nunca copiarlos
        // a completedSales.
        Integer products = SellerCommercialTextParser.parseExactListedProducts(
                request.sellerListedProductsText());
        if (products != null) {
            seller.setListedProductsCount(products);
            seller.setProductsObservedAt(now);
            activityUpdated = true;
        }

        // Solo una frase explícita permite afirmar que la plataforma declara
        // ventas insuficientes. La falta de cifras no significa ventas cero.
        boolean insufficientSales = SellerCommercialTextParser.explicitlyInsufficientSales(
                request.sellerInsufficientSalesText());
        if (insufficientSales) {
            seller.setInsufficientSalesForReputation(true);
            seller.setInsufficientSalesObservedAt(now);
            activityUpdated = true;
        }

        Integer exactSales = SellerSalesTextParser.parseExact(request.sellerSalesText());
        if (exactSales != null) {
            // Respetamos la vía numérica preexistente. La señal cuantitativa tiene
            // prioridad sobre las señales cualitativas en SellerActivityEvidence.
            seller.setCompletedSales(exactSales);
            seller.setSalesObservedAt(now);
            // No conservar señales cualitativas contradictorias antiguas.
            seller.setInsufficientSalesForReputation(null);
            seller.setInsufficientSalesObservedAt(null);
            seller.setSalesCountNotDisplayedObservedAt(null);
            sellerRepository.save(seller);
            return new SellerSalesObservationResponse("UPDATED", exactSales, now);
        }

        if (SellerSalesTextParser.isApproximate(request.sellerSalesText())) {
            // No borrar aquí una cifra exacta anterior que puede provenir de
            // la API oficial: esta pantalla no proporciona un reemplazo exacto.
            // Su validez seguirá limitada por salesObservedAt (30 días).
            if (activityUpdated) {
                sellerRepository.save(seller);
            }
            return new SellerSalesObservationResponse("APPROXIMATE_SALES",
                    seller.getCompletedSales(), seller.getSalesObservedAt());
        }

        if (activityUpdated) {
            sellerRepository.save(seller);
            return new SellerSalesObservationResponse("ACTIVITY_UPDATED",
                    seller.getCompletedSales(), seller.getSalesObservedAt());
        }

        // No recibimos ninguna señal válida: no inventamos información ni
        // actualizamos fechas para simular una observación exitosa.
        return new SellerSalesObservationResponse("NO_EXACT_SALES",
                seller.getCompletedSales(), seller.getSalesObservedAt());
    }

    private boolean sameDisplayedName(String stored, String received) {
        if (stored == null || received == null) {
            return false;
        }
        String a = normalizeName(stored);
        String b = normalizeName(received);
        return !a.isBlank() && a.equals(b);
    }

    private String normalizeName(String name) {
        // Compatibilidad con importaciones antiguas que pudieron conservar
        // el sufijo visible del catálogo en el nombre del vendedor.
        return name.strip()
                .replaceFirst("(?iu)^vendido\\s+por\\s+", "")
                .replaceFirst("(?iu),?\\s+\\d+\\s+productos?\\s*$", "")
                .strip()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}
