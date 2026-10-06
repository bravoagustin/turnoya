package com.gbravo.turnoya.payment;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gbravo.turnoya.payment.PaymentGateway.PaymentStatus;

/** Solo en desarrollo: simula que el cliente pagó y dispara el mismo flujo que el webhook real. */
@RestController
@Profile("!mercadopago")
public class DevPaymentController {

    private final FakePaymentGateway fakeGateway;
    private final PaymentNotificationService notifications;

    public DevPaymentController(FakePaymentGateway fakeGateway, PaymentNotificationService notifications) {
        this.fakeGateway = fakeGateway;
        this.notifications = notifications;
    }

    @PostMapping("/dev/payments/{bookingId}/approve")
    Map<String, String> approve(@PathVariable UUID bookingId) {
        String paymentId = fakeGateway.simulatePayment(bookingId, PaymentStatus.APPROVED);
        notifications.handlePaymentNotification(paymentId);
        return Map.of("paymentId", paymentId);
    }
}
