package com.gbravo.turnoya.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gbravo.turnoya.support.IntegrationTest;

/** Simula dos instancias de la app: mientras una tiene el candado del relay, la otra no publica nada. */
class OutboxRelayLockIT extends IntegrationTest {

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        // La base H2 es compartida con otros IT: marcamos lo pendiente para que el outbox quede solo con nuestros eventos.
        tx.executeWithoutResult(status -> outbox.findAll().stream()
                .filter(e -> e.getPublishedAt() == null)
                .forEach(e -> e.markPublished(clock.instant())));
    }

    @Test
    void mientrasOtraInstanciaTieneElCandadoElRelaySeSalteaElCicloSinEsperar() throws Exception {
        List<UUID> events = outbox.saveAll(List.of(newEvent(), newEvent())).stream().map(OutboxEvent::getId).toList();

        CountDownLatch otherHoldsLock = new CountDownLatch(1);
        CountDownLatch releaseOther = new CountDownLatch(1);

        // "Otra instancia": toma el candado y mantiene la transacción abierta, como mientras publica en Kafka.
        CompletableFuture<Void> other = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            assertThat(outbox.tryAcquireRelayLock()).isNotEmpty();
            otherHoldsLock.countDown();
            await(releaseOther);
        }));
        assertThat(otherHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

        // Si esperara el candado en vez de saltearlo, se quedaría bloqueado por el LOCK_TIMEOUT de H2 (10 s).
        int publishedWhileLocked = CompletableFuture.supplyAsync(relay::relayPending).get(5, TimeUnit.SECONDS);
        assertThat(publishedWhileLocked).isZero();
        assertThat(outbox.findAllById(events)).extracting(OutboxEvent::getPublishedAt).containsOnlyNulls();

        releaseOther.countDown();
        other.get(5, TimeUnit.SECONDS);

        assertThat(relay.relayPending()).isEqualTo(2);
        assertThat(outbox.findAllById(events)).extracting(OutboxEvent::getPublishedAt).doesNotContainNull();
    }

    private OutboxEvent newEvent() {
        return new OutboxEvent("booking", UUID.randomUUID(), "Test", "{}", clock.instant());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
