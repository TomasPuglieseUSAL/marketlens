package com.marketlens.backend.service;

import com.marketlens.backend.dto.CreateSellerRequest;
import com.marketlens.backend.dto.SellerResponse;
import com.marketlens.backend.entity.Seller;
import com.marketlens.backend.exception.ResourceNotFoundException;
import com.marketlens.backend.repository.SellerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import com.marketlens.backend.dto.SellerListItemResponse;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;

    public SellerService(SellerRepository sellerRepository) {
        this.sellerRepository = sellerRepository;
    }

    @Transactional
    public SellerResponse create(CreateSellerRequest request) {

        Seller seller = new Seller();

        seller.setName(request.name());
        seller.setReputation(request.reputation());
        seller.setAccountAgeDays(request.accountAgeDays());

        Seller savedSeller = sellerRepository.save(seller);

        return toResponse(savedSeller);
    }

    @Transactional(readOnly = true)
    public SellerResponse getById(Long id) {

        Seller seller = sellerRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Seller not found: " + id
                )
            );

        return toResponse(seller);
    }

    @Transactional(readOnly = true)
    public Page<SellerListItemResponse> getAll(Pageable pageable) {

    return sellerRepository.findAll(pageable)
        .map(seller -> new SellerListItemResponse(
            seller.getId(),
            seller.getName(),
            seller.getReputation(),
            seller.getAccountAgeDays()
        ));
    }

    private SellerResponse toResponse(Seller seller) {

        return new SellerResponse(
            seller.getId(),
            seller.getName(),
            seller.getReputation(),
            seller.getAccountAgeDays(),
            seller.getCreatedAt()
        );
    }
}