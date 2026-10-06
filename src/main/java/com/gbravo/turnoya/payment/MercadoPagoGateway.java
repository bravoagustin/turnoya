package com.gbravo.turnoya.payment;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gbravo.turnoya.shared.TurnoYaProperties;

/**
 * Integración con la API REST de Mercado Pago (Checkout Pro).
 * Activar con el perfil "mercadopago" y la variable MP_ACCESS_TOKEN.
 */
@Component
@Profile("mercadopago")
public class MercadoPagoGateway implements PaymentGateway {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
    };
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .withZone(ZoneOffset.UTC);

    private final RestClient client;
    private final TurnoYaProperties.MercadoPago config;

    public MercadoPagoGateway(TurnoYaProperties properties) {
        this.config = properties.mercadopago();
        if (config.accessToken() == null || config.accessToken().isBlank()) {
            throw new IllegalStateException("Falta MP_ACCESS_TOKEN para el perfil mercadopago");
        }
        this.client = RestClient.builder()
                .baseUrl("https://api.mercadopago.com")
                .defaultHeader("Authorization", "Bearer " + config.accessToken())
                .build();
    }

    @Override
    public CheckoutLink createCheckout(CheckoutRequest request) {
        Map<String, Object> body = Map.of(
                "items", List.of(Map.of(
                        "title", request.title(),
                        "quantity", 1,
                        "currency_id", "ARS",
                        "unit_price", request.amount())),
                "external_reference", request.bookingId().toString(),
                "notification_url", config.notificationUrl(),
                // El link deja de servir cuando vence la seña.
                "expires", true,
                "expiration_date_to", MP_DATE.format(request.expiresAt()));
        try {
            Map<String, Object> response = client.post()
                    .uri("/checkout/preferences")
                    // Si reintentamos por un timeout, Mercado Pago no crea una preferencia duplicada.
                    .header("X-Idempotency-Key", request.bookingId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JSON_MAP);
            return new CheckoutLink(String.valueOf(response.get("id")), String.valueOf(response.get("init_point")));
        } catch (RestClientException e) {
            throw new PaymentProviderException("Error creando la preferencia en Mercado Pago", e);
        }
    }

    @Override
    public PaymentInfo fetchPayment(String paymentId) {
        try {
            Map<String, Object> payment = client.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .retrieve()
                    .body(JSON_MAP);
            return new PaymentInfo(
                    String.valueOf(payment.get("id")),
                    PaymentStatus.fromMercadoPago((String) payment.get("status")),
                    (String) payment.get("external_reference"),
                    new BigDecimal(String.valueOf(payment.get("transaction_amount"))));
        } catch (RestClientException e) {
            throw new PaymentProviderException("Error consultando el pago " + paymentId, e);
        }
    }
}
