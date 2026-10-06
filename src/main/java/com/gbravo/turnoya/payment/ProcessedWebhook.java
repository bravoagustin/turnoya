package com.gbravo.turnoya.payment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_webhook")
public class ProcessedWebhook {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(name = "event_key", nullable = false)
    private String eventKey;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedWebhook() {
    }

    public ProcessedWebhook(String provider, String eventKey, Instant now) {
        this.id = UUID.randomUUID();
        this.provider = provider;
        this.eventKey = eventKey;
        this.processedAt = now;
    }
}
