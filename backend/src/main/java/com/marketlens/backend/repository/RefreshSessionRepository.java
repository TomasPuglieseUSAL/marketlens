package com.marketlens.backend.repository;

import com.marketlens.backend.entity.RefreshSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {
    // Bloqueo por sesión: una sola renovación puede rotar el token a la vez.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s join fetch s.user where s.id = :id")
    Optional<RefreshSession> findForUpdate(@Param("id") Long id);
}
