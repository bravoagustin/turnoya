package com.gbravo.turnoya.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

    private final KafkaEventPublisher publisher = new KafkaEventPublisher(kafka, Duration.ofMillis(200));

    private final OutboxEvent event = new OutboxEvent("booking", UUID.randomUUID(), "BookingConfirmed",
            "{\"paymentId\":\"123\"}", Instant.parse("2026-10-05T12:00:00Z"));

    @Test
    @SuppressWarnings("unchecked")
    void publicaEnElTopicDelAgregadoConElIdComoKeyYHeadersParaDeduplicar() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        publisher.publish(event);

        ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafka).send(sent.capture());
        ProducerRecord<String, String> record = sent.getValue();
        assertThat(record.topic()).isEqualTo("turnoya.booking");
        assertThat(record.key()).isEqualTo(event.getAggregateId().toString());
        assertThat(record.value()).isEqualTo(event.getPayload());
        assertThat(header(record, KafkaEventPublisher.EVENT_ID_HEADER)).isEqualTo(event.getId().toString());
        assertThat(header(record, KafkaEventPublisher.EVENT_TYPE_HEADER)).isEqualTo("BookingConfirmed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElBrokerRechazaElEnvioLanzaExcepcionParaQueElRelayReintente() {
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caído")));

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(EventPublishException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void siElBrokerNoConfirmaATiempoLanzaExcepcionEnVezDeQuedarseEsperando() {
        when(kafka.send(any(ProducerRecord.class))).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> publisher.publish(event)).isInstanceOf(EventPublishException.class);
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
