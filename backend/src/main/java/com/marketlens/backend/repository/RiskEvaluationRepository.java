package com.marketlens.backend.repository;

import com.marketlens.backend.entity.RiskEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface RiskEvaluationRepository
        extends JpaRepository<RiskEvaluation, Long> {

    List<RiskEvaluation> findByPublicationIdOrderByEvaluatedAtDesc(
        Long publicationId
    );

    @Query("""
    SELECT re
    FROM RiskEvaluation re
    WHERE re.id = (
        SELECT MAX(re2.id)
        FROM RiskEvaluation re2
        WHERE re2.publication = re.publication
    )
    """)
    List<RiskEvaluation> findLatestEvaluations();
}