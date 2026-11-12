package com.marketlens.backend.repository;

import com.marketlens.backend.entity.ExtensionImport;
import com.marketlens.backend.entity.Marketplace;
import com.marketlens.backend.entity.Publication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface ExtensionImportRepository
                extends JpaRepository<ExtensionImport, Long> {
        Optional<ExtensionImport> findByIdAndImportedBy(
                        Long id,
                        String importedBy);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                        SELECT i
                        FROM ExtensionImport i
                        WHERE i.id = :id
                          AND i.importedBy = :username
                        """)
        Optional<ExtensionImport> findOwnedForProcessing(
                        @Param("id") Long id,
                        @Param("username") String username);

        Optional<Publication> findByMarketplaceAndExternalId(
                        Marketplace marketplace,
                        String externalId);
}