package com.gbravo.turnoya.payment;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gbravo.turnoya.shared.NotFoundException;
import com.gbravo.turnoya.shared.TurnoYaProperties;

/** Simula Mercado Pago en memoria para desarrollo y tests. Se reemplaza con el perfil "mercadopago". */
@Component
@Profile("!mercadopago")
public class FakePaymentGateway implements PaymentGateway {

    private final Map<String, PaymentInfo> payments = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> checkouts = new ConcurrentHashMap<>();
    private final String baseUrl;

    public FakePaymentGateway(TurnoYaProperties properties) {
        this.baseUrl = properties.publicBaseUrl();
    }

    @Override
    public CheckoutLink createCheckout(CheckoutRequest request) {
        checkouts.put(request.bookingId(), request.amount());
        return new CheckoutLink("fake-pref-" + request.bookingId(),
                baseUrl + "/dev/payments/" + request.bookingId() + "/approve");
    }

    @Override
    public PaymentInfo fetchPayment(String paymentId) {
        return Optional.ofNullable(payments.get(paymentId))
                .orElseThrow(() -> new NotFoundException("Pago", paymentId));
    }

    /** Simula que el cliente pagó la seña. Devuelve el id del pago, como lo haría Mercado Pago. */
    public String simulatePayment(UUID bookingId, PaymentStatus status) {
        BigDecimal amount = Optional.ofNullable(checkouts.get(bookingId))
                .orElseThrow(() -> new NotFoundException("Checkout de la reserva", bookingId));
        String paymentId = "fake-" + UUID.randomUUID();
        payments.put(paymentId, new PaymentInfo(paymentId, status, bookingId.toString(), amount));
        return paymentId;
    }
}
