package com.gbravo.turnoya.booking;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gbravo.turnoya.booking.AvailabilityService.Slot;
import com.gbravo.turnoya.booking.BookingDtos.AgendaEntry;
import com.gbravo.turnoya.booking.BookingDtos.BookingResponse;
import com.gbravo.turnoya.booking.BookingDtos.CreateBookingRequest;

import jakarta.validation.Valid;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final AvailabilityService availabilityService;

    public BookingController(BookingService bookingService, AvailabilityService availabilityService) {
        this.bookingService = bookingService;
        this.availabilityService = availabilityService;
    }

    @GetMapping("/api/businesses/{businessId}/availability")
    List<Slot> availability(@PathVariable UUID businessId, @RequestParam UUID serviceId,
            @RequestParam LocalDate date) {
        return availabilityService.availableSlots(businessId, serviceId, date);
    }

    @PostMapping("/api/businesses/{businessId}/bookings")
    ResponseEntity<BookingResponse> reserve(@PathVariable UUID businessId,
            @Valid @RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.reserve(businessId, request);
        return ResponseEntity.created(URI.create("/api/bookings/" + booking.getId()))
                .body(BookingResponse.from(booking));
    }

    @GetMapping("/api/businesses/{businessId}/bookings")
    List<AgendaEntry> agenda(@PathVariable UUID businessId, @RequestParam LocalDate date) {
        return bookingService.agenda(businessId, date).stream().map(AgendaEntry::from).toList();
    }

    @GetMapping("/api/bookings/{bookingId}")
    BookingResponse get(@PathVariable UUID bookingId) {
        return BookingResponse.from(bookingService.get(bookingId));
    }
}
