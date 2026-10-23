package com.gbravo.turnoya.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Intenta tomar el candado del relay (una fila en {@code outbox_relay_lock}) hasta que termine la transacción.
     * Devuelve vacío si otra instancia ya lo tiene: con SKIP LOCKED no se queda esperando. SQL nativo porque JPQL
     * no tiene SKIP LOCKED; funciona igual en PostgreSQL y en H2 2.x.
     */
    @Query(value = "select id from outbox_relay_lock where id = 1 for update skip locked", nativeQuery = true)
    List<Integer> tryAcquireRelayLock();

    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt")
    List<OutboxEvent> findUnpublished(Pageable page);

    @Modifying
    @Query("delete from OutboxEvent e where e.publishedAt is not null and e.publishedAt < :cutoff")
    int deletePublishedBefore(Instant cutoff);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
}
