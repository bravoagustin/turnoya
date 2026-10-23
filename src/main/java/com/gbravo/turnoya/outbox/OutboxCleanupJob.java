package com.gbravo.turnoya.outbox;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra los eventos ya publicados después de un tiempo de retención, para que el outbox no crezca sin límite.
 * Los pendientes nunca se borran. Es idempotente: si corre en varias instancias, la segunda no encuentra nada.
 */
@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxRepository outbox;
    private final Clock clock;
    private final Duration retention;

    public OutboxCleanupJob(OutboxRepository outbox, Clock clock,
            @Value("${turnoya.outbox.retention:7d}") Duration retention) {
        this.outbox = outbox;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${turnoya.outbox.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public int purgePublished() {
        int deleted = outbox.deletePublishedBefore(clock.instant().minus(retention));
        if (deleted > 0) {
            log.info("Outbox: {} eventos publicados hace más de {} borrados", deleted, retention);
        }
        return deleted;
    }
}
