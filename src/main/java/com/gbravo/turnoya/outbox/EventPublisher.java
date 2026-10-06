package com.gbravo.turnoya.outbox;

/** Destino de los eventos del outbox. Hoy: logs. Etapa 2: Kafka (topic por tipo de agregado). */
public interface EventPublisher {

    void publish(OutboxEvent event);
}
