package com.gbravo.turnoya.booking;

/**
 * Ciclo de vida de una reserva.
 *
 * <pre>
 * PENDING_PAYMENT ──(pago aprobado)──▶ CONFIRMED
 *       │
 *       ├──(vence la seña)──▶ EXPIRED ──(pago tardío, horario libre)──▶ CONFIRMED
 *       │                        └────(pago tardío, horario tomado)──▶ REFUND_REQUIRED
 *       └──(falla el checkout)──▶ CANCELLED
 * </pre>
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    EXPIRED,
    CANCELLED,
    REFUND_REQUIRED
}
