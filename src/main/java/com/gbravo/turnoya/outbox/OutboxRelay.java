package com.gbravo.turnoya.outbox;

import java.time.Clock;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publica los eventos pendientes del outbox. Entrega "al menos una vez":
 * si se cae después de publicar y antes de marcar, el evento se reenvía, así que los consumidores deben ser idempotentes.
 * Con varias instancias, solo publica la que toma el candado del relay: así se conserva el orden de los eventos.
 */
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outbox;
    private final EventPublisher publisher;
    private final Clock clock;

    public OutboxRelay(OutboxRepository outbox, EventPublisher publisher, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${turnoya.outbox.relay-interval:PT2S}")
    @Transactional
    public int relayPending() {
        if (outbox.tryAcquireRelayLock().isEmpty()) {
            // Otra instancia está publicando; lo intentamos en el próximo ciclo.
            return 0;
        }
        List<OutboxEvent> pending = outbox.findUnpublished(PageRequest.of(0, BATCH_SIZE));
        for (OutboxEvent event : pending) {
            publisher.publish(event);
            event.markPublished(clock.instant());
        }
        return pending.size();
    }
}
