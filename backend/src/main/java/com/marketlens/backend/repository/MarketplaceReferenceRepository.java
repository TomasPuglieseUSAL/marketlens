package com.marketlens.backend.repository;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.MarketplaceReference;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketplaceReferenceRepository
        extends JpaRepository<MarketplaceReference, Long> {

    Optional<MarketplaceReference>
        findByMarketplaceAndReferenceTypeAndExternalId(
            Marketplace marketplace,
            String referenceType,
            String externalId
        );
}