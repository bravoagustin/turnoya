package com.gbravo.turnoya.outbox;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/** KafkaAdmin crea los topics al arrancar si no existen. Un topic por tipo de agregado. */
@Configuration
@Profile("kafka")
class KafkaTopicsConfig {

    @Bean
    NewTopic bookingTopic() {
        return TopicBuilder.name(KafkaEventPublisher.topicFor("booking")).partitions(3).replicas(1).build();
    }
}
