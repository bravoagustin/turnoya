package com.gbravo.turnoya.booking;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca como vencidas las señas impagas. No es crítico para la agenda (una seña vencida ya no bloquea
 * el horario), pero deja el estado prolijo y emite el evento BookingExpired.
 * Cada reserva vence en su propia transacción para que un conflicto no frene al resto.
 */
@Component
public class HoldExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpirationJob.class);
    private static final int BATCH_SIZE = 200;

    private final BookingRepository bookings;
    private final BookingService bookingService;
    private final Clock clock;

    public HoldExpirationJob(BookingRepository bookings, BookingService bookingService, Clock clock) {
        this.bookings = bookings;
        this.bookingService = bookingService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${turnoya.booking.expiration-check:PT30S}")
    public int expireDueHolds() {
        List<UUID> due = bookings.findExpiredHoldIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
        int expired = 0;
        for (UUID id : due) {
            try {
                if (bookingService.expireHold(id)) {
                    expired++;
                }
            } catch (ObjectOptimisticLockingFailureException e) {
                // Llegó el pago al mismo tiempo: gana el webhook, no la vencemos.
                log.info("Reserva {} modificada concurrentemente, se omite", id);
            }
        }
        return expired;
    }
}
