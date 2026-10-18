package com.gbravo.turnoya.outbox;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos del outbox en Kafka: un topic por tipo de agregado y el id del agregado como key,
 * así los eventos de una misma reserva conservan el orden. Lanza excepción si el broker no confirma:
 * el relay hace rollback y el evento se reintenta en el próximo ciclo.
 */
@Component
@Profile("kafka")
public class KafkaEventPublisher implements EventPublisher {

    public static final String EVENT_ID_HEADER = "eventId";
    public static final String EVENT_TYPE_HEADER = "eventType";

    private final KafkaTemplate<String, String> kafka;
    private final Duration sendTimeout;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka,
            @Value("${turnoya.outbox.send-timeout:5s}") Duration sendTimeout) {
        this.kafka = kafka;
        this.sendTimeout = sendTimeout;
    }

    static String topicFor(String aggregateType) {
        return "turnoya." + aggregateType;
    }

    @Override
    public void publish(OutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topicFor(event.getAggregateType()),
                event.getAggregateId().toString(), event.getPayload());
        record.headers().add(EVENT_ID_HEADER, event.getId().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EVENT_TYPE_HEADER, event.getEventType().getBytes(StandardCharsets.UTF_8));

        try {
            kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EventPublishException("Interrumpido publicando el evento " + event.getId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new EventPublishException("No se pudo publicar el evento %s %s en %s"
                    .formatted(event.getEventType(), event.getId(), record.topic()), e);
        }
    }
}
