package com.gbravo.turnoya.business;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BusinessRepository extends JpaRepository<Business, UUID> {

    /**
     * SELECT ... FOR UPDATE sobre el negocio: serializa la toma de turnos de un mismo negocio
     * sin bloquear a los demás negocios.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Business b where b.id = :id")
    Optional<Business> findByIdForUpdate(@Param("id") UUID id);

    boolean existsBySlug(String slug);
}
