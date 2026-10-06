package com.gbravo.turnoya.shared;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("turnoya")
public record TurnoYaProperties(String publicBaseUrl, Booking booking, MercadoPago mercadopago) {

    public record Booking(Duration holdTtl, int maxDaysAhead) {
    }

    public record MercadoPago(String accessToken, String webhookSecret, String notificationUrl) {
    }
}
