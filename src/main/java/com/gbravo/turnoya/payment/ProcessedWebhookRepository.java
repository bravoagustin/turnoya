package com.gbravo.turnoya.payment;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedWebhookRepository extends JpaRepository<ProcessedWebhook, UUID> {

    boolean existsByProviderAndEventKey(String provider, String eventKey);
}
