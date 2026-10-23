package com.gbravo.turnoya.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gbravo.turnoya.support.IntegrationTest;

class OutboxCleanupIT extends IntegrationTest {

    @Autowired
    private OutboxCleanupJob cleanup;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void borraSoloLosEventosPublicadosHaceMasQueLaRetencion() {
        OutboxEvent old = outbox.save(publishedAgo(Duration.ofDays(8)));
        OutboxEvent recent = outbox.save(publishedAgo(Duration.ofDays(6)));
        OutboxEvent pending = outbox.save(new OutboxEvent("booking", UUID.randomUUID(), "Test", "{}",
                NOW.minus(Duration.ofDays(30))));

        int deleted = cleanup.purgePublished();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(outbox.existsById(old.getId())).isFalse();
        assertThat(outbox.existsById(recent.getId())).as("publicado dentro de la retención de 7 días").isTrue();
        assertThat(outbox.existsById(pending.getId())).as("los pendientes nunca se borran").isTrue();
    }

    private static OutboxEvent publishedAgo(Duration age) {
        OutboxEvent event = new OutboxEvent("booking", UUID.randomUUID(), "Test", "{}", NOW.minus(age));
        event.markPublished(NOW.minus(age));
        return event;
    }
}
