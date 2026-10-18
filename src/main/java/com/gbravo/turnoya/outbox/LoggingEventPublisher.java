package com.gbravo.turnoya.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!kafka")
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("evento {} {}={} {}", event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                event.getPayload());
    }
}
