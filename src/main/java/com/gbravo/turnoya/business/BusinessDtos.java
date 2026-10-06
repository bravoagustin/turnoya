package com.gbravo.turnoya.business;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BusinessDtos {

    private BusinessDtos() {
    }

    public record CreateBusinessRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{3,80}$", message = "solo minúsculas, números y guiones") String slug,
            @NotBlank @ValidTimezone String timezone,
            @NotNull LocalTime openingTime,
            @NotNull LocalTime closingTime,
            @Min(5) @Max(240) int slotMinutes) {
    }

    public record BusinessResponse(UUID id, String name, String slug, String timezone, LocalTime openingTime,
            LocalTime closingTime, int slotMinutes) {

        static BusinessResponse from(Business b) {
            return new BusinessResponse(b.getId(), b.getName(), b.getSlug(), b.getZoneId().getId(),
                    b.getOpeningTime(), b.getClosingTime(), b.getSlotMinutes());
        }
    }

    public record CreateServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Min(5) @Max(480) int durationMinutes,
            @NotNull @DecimalMin("1.00") BigDecimal price,
            @Min(1) @Max(100) int depositPercent) {
    }

    public record ServiceResponse(UUID id, String name, int durationMinutes, BigDecimal price, int depositPercent,
            BigDecimal depositAmount) {

        static ServiceResponse from(ServiceOffering s) {
            return new ServiceResponse(s.getId(), s.getName(), s.getDurationMinutes(), s.getPrice(),
                    s.getDepositPercent(), s.depositAmount());
        }
    }
}
