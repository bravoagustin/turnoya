package com.gbravo.turnoya.business;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {

    Optional<ServiceOffering> findByIdAndBusinessId(UUID id, UUID businessId);

    List<ServiceOffering> findByBusinessIdAndActiveTrueOrderByName(UUID businessId);
}
