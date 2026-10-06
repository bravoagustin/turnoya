package com.gbravo.turnoya.booking;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.business.BusinessService;
import com.gbravo.turnoya.business.ServiceOffering;

@Service
public class AvailabilityService {

    public record Slot(Instant startAt, Instant endAt) {
    }

    private final BusinessService businessService;
    private final BookingRepository bookings;
    private final Clock clock;

    public AvailabilityService(BusinessService businessService, BookingRepository bookings, Clock clock) {
        this.businessService = businessService;
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Slot> availableSlots(UUID businessId, UUID serviceId, LocalDate date) {
        Business business = businessService.get(businessId);
        ServiceOffering service = businessService.getService(businessId, serviceId);
        Instant now = clock.instant();

        List<Instant> starts = ScheduleRules.candidateStarts(business, date, service.duration());
        if (starts.isEmpty()) {
            return List.of();
        }
        Instant dayFrom = starts.getFirst();
        Instant dayTo = starts.getLast().plus(service.duration());
        // Una sola consulta por día: traemos lo que bloquea la agenda y filtramos en memoria.
        List<Booking> blocking = bookings.findBlocking(businessId, dayFrom, dayTo, now);

        return starts.stream()
                .filter(start -> start.isAfter(now))
                .map(start -> new Slot(start, start.plus(service.duration())))
                .filter(slot -> blocking.stream().noneMatch(b -> b.overlaps(slot.startAt(), slot.endAt())))
                .toList();
    }
}
