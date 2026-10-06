package com.gbravo.turnoya.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Puerto hacia el proveedor de pagos. Implementaciones: Mercado Pago (prod) y Fake (desarrollo/tests). */
public interface PaymentGateway {

    CheckoutLink createCheckout(CheckoutRequest request);

    PaymentInfo fetchPayment(String paymentId);

    record CheckoutRequest(UUID bookingId, String title, BigDecimal amount, Instant expiresAt) {
    }

    record CheckoutLink(String preferenceId, String url) {
    }

    /** externalReference = id de la reserva, así vinculamos el pago con el turno. */
    record PaymentInfo(String id, PaymentStatus status, String externalReference, BigDecimal amount) {
    }

    enum PaymentStatus {
        APPROVED, PENDING, REJECTED, REFUNDED, OTHER;

        public static PaymentStatus fromMercadoPago(String status) {
            return switch (status == null ? "" : status) {
                case "approved" -> APPROVED;
                case "pending", "in_process", "authorized" -> PENDING;
                case "rejected", "cancelled" -> REJECTED;
                case "refunded", "charged_back" -> REFUNDED;
                default -> OTHER;
            };
        }
    }
}
