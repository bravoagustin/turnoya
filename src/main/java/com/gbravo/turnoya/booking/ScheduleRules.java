package com.gbravo.turnoya.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.shared.BusinessRuleException;

/** Reglas puras de agenda (sin base de datos), fáciles de testear unitariamente. */
public final class ScheduleRules {

    private ScheduleRules() {
    }

    /** Horarios de inicio posibles de un día, en la zona horaria del negocio. */
    public static List<Instant> candidateStarts(Business business, LocalDate date, Duration serviceDuration) {
        List<Instant> starts = new ArrayList<>();
        Duration step = Duration.ofMinutes(business.getSlotMinutes());
        LocalTime closing = business.getClosingTime();
        for (LocalTime t = business.getOpeningTime(); !t.plus(serviceDuration).isAfter(closing); t = t.plus(step)) {
            starts.add(ZonedDateTime.of(date, t, business.getZoneId()).toInstant());
            if (t.plus(step).isBefore(t)) {
                break; // pasó la medianoche
            }
        }
        return starts;
    }

    public static void ensureBookable(Business business, Instant startAt, Duration serviceDuration, Instant now,
            int maxDaysAhead) {
        if (!startAt.isAfter(now)) {
            throw new BusinessRuleException("El horario ya pasó");
        }
        if (startAt.isAfter(now.plus(Duration.ofDays(maxDaysAhead)))) {
            throw new BusinessRuleException("Solo se puede reservar hasta " + maxDaysAhead + " días adelante");
        }
        ZonedDateTime localStart = startAt.atZone(business.getZoneId());
        ZonedDateTime localEnd = localStart.plus(serviceDuration);
        LocalTime start = localStart.toLocalTime();
        if (!localEnd.toLocalDate().equals(localStart.toLocalDate())
                || start.isBefore(business.getOpeningTime())
                || localEnd.toLocalTime().isAfter(business.getClosingTime())) {
            throw new BusinessRuleException("El turno debe quedar dentro del horario de atención "
                    + business.getOpeningTime() + "-" + business.getClosingTime());
        }
        long minutesFromOpening = Duration.between(business.getOpeningTime(), start).toMinutes();
        if (start.getSecond() != 0 || start.getNano() != 0 || minutesFromOpening % business.getSlotMinutes() != 0) {
            throw new BusinessRuleException("Los turnos empiezan cada " + business.getSlotMinutes() + " minutos");
        }
    }
}
