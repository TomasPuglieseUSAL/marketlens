package com.marketlens.backend.repository;

import com.marketlens.backend.entity.Report;
import com.marketlens.backend.entity.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ReportRepository
        extends JpaRepository<Report, Long> {

    List<Report> findByPublicationIdOrderByCreatedAtDesc(
        Long publicationId
    );

    // Ventana acotada, incluyendo identidad del autor en una sola consulta
    // para contar usuarios distintos sin filtrar reportes fuera del período.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    List<Report> findByPublicationIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long publicationId, java.time.LocalDateTime from, java.time.LocalDateTime to);

    @Query("""
    SELECT COUNT(r)
    FROM Report r
    WHERE r.publication.marketplace = :marketplace
    """)
    long countByMarketplace(Marketplace marketplace);
}