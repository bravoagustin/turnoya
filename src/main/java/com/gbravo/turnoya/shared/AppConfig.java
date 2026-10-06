package com.gbravo.turnoya.shared;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
public class AppConfig {

    /** Reloj inyectable: permite testear vencimientos de señas sin esperar minutos reales. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "turnoya.scheduling.enabled", havingValue = "true", matchIfMissing = true)
    static class SchedulingConfig {
    }
}
