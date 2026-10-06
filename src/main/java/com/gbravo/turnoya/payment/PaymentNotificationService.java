package com.gbravo.turnoya.payment;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.gbravo.turnoya.booking.BookingService;
import com.gbravo.turnoya.payment.PaymentGateway.PaymentInfo;
import com.gbravo.turnoya.payment.PaymentGateway.PaymentStatus;

@Service
public class PaymentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);
    private static final String PROVIDER = "mercadopago";

    public enum Outcome { PROCESSED, DUPLICATE, IGNORED }

    private final PaymentGateway gateway;
    private final ProcessedWebhookRepository processed;
    private final BookingService bookingService;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentNotificationService(PaymentGateway gateway, ProcessedWebhookRepository processed,
            BookingService bookingService, PlatformTransactionManager txManager, Clock clock) {
        this.gateway = gateway;
        this.processed = processed;
        this.bookingService = bookingService;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    /**
     * Procesa cada (pago, estado) una sola vez. La marca de "procesado" y el cambio en la reserva
     * van en la misma transacción: o pasan las dos cosas o ninguna (y Mercado Pago reintenta).
     */
    public Outcome handlePaymentNotification(String paymentId) {
        // Consulta HTTP fuera de la transacción: no retenemos conexiones de base esperando a la red.
        PaymentInfo payment = gateway.fetchPayment(paymentId);
        String eventKey = payment.id() + ":" + payment.status();
        try {
            return tx.execute(status -> {
                if (processed.existsByProviderAndEventKey(PROVIDER, eventKey)) {
                    return Outcome.DUPLICATE;
                }
                // saveAndFlush: si dos webhooks iguales llegan a la vez, la unique constraint frena al segundo acá.
                processed.saveAndFlush(new ProcessedWebhook(PROVIDER, eventKey, clock.instant()));
                if (payment.status() != PaymentStatus.APPROVED || payment.externalReference() == null) {
                    return Outcome.IGNORED;
                }
                bookingService.applyApprovedPayment(UUID.fromString(payment.externalReference()), payment.id(),
                        payment.amount());
                return Outcome.PROCESSED;
            });
        } catch (DataIntegrityViolationException e) {
            log.info("Webhook duplicado concurrente para {}", eventKey);
            return Outcome.DUPLICATE;
        }
    }
}
