package com.gbravo.turnoya.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEventPublisher.class);

    @Override
    public void publish(OutboxEvent event) {
        log.info("evento {} {}={} {}", event.getEventType(), event.getAggregateType(), event.getAggregateId(),
                event.getPayload());
    }
}
