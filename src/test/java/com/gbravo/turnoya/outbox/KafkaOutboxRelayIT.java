package com.gbravo.turnoya.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import com.gbravo.turnoya.business.ServiceOffering;
import com.gbravo.turnoya.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

/** Broker en memoria (sin Docker); el topic lo crea KafkaAdmin a partir de KafkaTopicsConfig. */
@ActiveProfiles("kafka")
@EmbeddedKafka(bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class KafkaOutboxRelayIT extends IntegrationTest {

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private OutboxRepository outbox;

    @Value("${spring.kafka.bootstrap-servers}")
    private String brokers;

    @Test
    void elRelayPublicaLosEventosDelOutboxEnKafkaYLosMarcaComoPublicados() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        String bookingId = reserve(service);

        // La base H2 es compartida con otros IT y el scheduling está apagado: puede haber eventos pendientes de antes.
        while (relay.relayPending() > 0) {
        }

        ConsumerRecord<String, String> record = pollForKey("turnoya.booking", bookingId)
                .orElseThrow(() -> new AssertionError("No llegó a Kafka ningún evento de la reserva " + bookingId));
        assertThat(header(record, KafkaEventPublisher.EVENT_TYPE_HEADER)).isEqualTo("BookingHeld");
        assertThat(JsonPath.<String>read(record.value(), "$.startAt")).isEqualTo(TOMORROW_10AM.toString());

        List<OutboxEvent> events = outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(bookingId));
        assertThat(events).extracting(OutboxEvent::getPublishedAt).doesNotContainNull();
        assertThat(header(record, KafkaEventPublisher.EVENT_ID_HEADER)).isEqualTo(events.getFirst().getId().toString());
    }

    private String reserve(ServiceOffering service) throws Exception {
        String body = mvc.perform(post("/api/businesses/{id}/bookings", service.getBusiness().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"serviceId":"%s","customerName":"Ana","customerPhone":"3518000001","startAt":"%s"}
                                """.formatted(service.getId(), TOMORROW_10AM)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private Optional<ConsumerRecord<String, String>> pollForKey(String topic, String key) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(15);
            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (key.equals(record.key())) {
                        return Optional.of(record);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        assertThat(header).as("header %s", name).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
