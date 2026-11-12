package com.marketlens.backend.repository;

import com.marketlens.backend.entity.Publication;
import com.marketlens.backend.entity.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;


public interface PublicationRepository extends JpaRepository<Publication, Long> {
    long countByMarketplace(Marketplace marketplace);

    @Query("""
                SELECT p.marketplace, COUNT(p)
                FROM Publication p
                GROUP BY p.marketplace
            """)
    List<Object[]> countPublicationsByMarketplace();

    Optional<Publication> findByMarketplaceAndExternalId(
            Marketplace marketplace,
            String externalId);

    List<Publication> findByIdNotAndPriceGreaterThanOrderByCreatedAtDesc(
            Long excludedId,
            BigDecimal minimumPrice,
            Pageable pageable);
}