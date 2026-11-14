package com.marketlens.backend.service;

import com.marketlens.backend.dto.CreatePublicationRequest;
import com.marketlens.backend.dto.PublicationResponse;
import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.PublicationRepository;
import com.marketlens.backend.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.marketlens.backend.dto.PublicationListItemResponse;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final SellerRepository sellerRepository;

    public PublicationService(
            PublicationRepository publicationRepository,
            SellerRepository sellerRepository) {

        this.publicationRepository = publicationRepository;
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public PublicationResponse create(CreatePublicationRequest request) {

        Seller seller = sellerRepository
            .findById(request.sellerId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Seller not found: " + request.sellerId()
                )
            );

        Publication publication = new Publication();

        publication.setTitle(request.title());
        publication.setDescription(request.description());
        publication.setPrice(request.price());
        publication.setSeller(seller);
        publication.setMarketplace(request.marketplace());
        publication.setExternalId(request.externalId());
        publication.setOriginalUrl(request.originalUrl());

        Publication savedPublication =
            publicationRepository.save(publication);

        return toResponse(savedPublication);
    }

    @Transactional(readOnly = true)
    public PublicationResponse getById(Long id) {

        Publication publication = publicationRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Publication not found: " + id
                )
            );

        return toResponse(publication);
    }

    @Transactional(readOnly = true)
    public Page<PublicationListItemResponse> getAll(Pageable pageable) {

    return publicationRepository.findAll(pageable)
        .map(publication -> new PublicationListItemResponse(
            publication.getId(),
            publication.getTitle(),
            publication.getPrice(),
            publication.getMarketplace(),
            publication.getSeller() == null ? null : publication.getSeller().getId(),
            publication.getSeller() == null ? null : publication.getSeller().getName()
        ));
    }

    private PublicationResponse toResponse(
        Publication publication) {

    return new PublicationResponse(
        publication.getId(),
        publication.getTitle(),
        publication.getDescription(),
        publication.getPrice(),
        publication.getSeller() == null ? null : publication.getSeller().getId(),
        publication.getMarketplace(),
        publication.getExternalId(),
        publication.getOriginalUrl(),
        publication.getCreatedAt()
    );
}
}