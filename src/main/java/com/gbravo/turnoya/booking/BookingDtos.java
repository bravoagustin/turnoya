package com.gbravo.turnoya.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BookingDtos {

    private BookingDtos() {
    }

    public record CreateBookingRequest(
            @NotNull UUID serviceId,
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "teléfono inválido") String customerPhone,
            @NotNull Instant startAt) {
    }

    public record AgendaEntry(UUID id, Instant startAt, Instant endAt, String serviceName, String customerName,
            String customerPhone, BookingStatus status, BigDecimal depositAmount, Instant holdExpiresAt) {

        static AgendaEntry from(Booking b) {
            return new AgendaEntry(b.getId(), b.getStartAt(), b.getEndAt(), b.getService().getName(),
                    b.getCustomerName(), b.getCustomerPhone(), b.getStatus(), b.getDepositAmount(),
                    b.getHoldExpiresAt());
        }
    }

    public record BookingResponse(UUID id, UUID businessId, UUID serviceId, String customerName, Instant startAt,
            Instant endAt, BookingStatus status, BigDecimal depositAmount, Instant holdExpiresAt, String checkoutUrl) {

        static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getBusiness().getId(), b.getService().getId(), b.getCustomerName(),
                    b.getStartAt(), b.getEndAt(), b.getStatus(), b.getDepositAmount(), b.getHoldExpiresAt(),
                    b.getCheckoutUrl());
        }
    }
}
