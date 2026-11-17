package com.marketlens.backend.service;

import com.marketlens.backend.dto.SellerRefreshResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compatibilidad del endpoint antiguo de refresh-seller.
 * Ya no consulta la API oficial. Nunca simula una verificación ni
 * sobrescribe observaciones obtenidas desde la extensión.
 */
@Service
public class SellerRefreshService {

    private final PublicationRepository publicationRepository;

    public SellerRefreshService(PublicationRepository publicationRepository) {
        this.publicationRepository = publicationRepository;
    }

    @Transactional(readOnly = true)
    public SellerRefreshResponse refresh(Long publicationId, String username) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Publication not found: " + publicationId));
        Seller seller = publication.getSeller();
        return new SellerRefreshResponse("NOT_SUPPORTED",
                seller == null ? null : seller.getCompletedSales(),
                seller == null ? null : seller.getSalesObservedAt());
    }
}
