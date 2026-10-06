package com.gbravo.turnoya.outbox;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Guarda un evento de dominio en la misma transacción que el cambio de estado.
 * Si la transacción hace rollback, el evento tampoco existe: nunca se publica algo que no pasó.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outbox;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public OutboxWriter(OutboxRepository outbox, JsonMapper jsonMapper, Clock clock) {
        this.outbox = outbox;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload) {
        String json = jsonMapper.writeValueAsString(payload);
        outbox.save(new OutboxEvent(aggregateType, aggregateId, eventType, json, clock.instant()));
    }
}
