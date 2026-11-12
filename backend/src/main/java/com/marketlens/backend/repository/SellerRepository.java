package com.marketlens.backend.repository;

import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Seller;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByMarketplaceAndExternalSellerId(
            Marketplace marketplace,
            Long externalSellerId);
}