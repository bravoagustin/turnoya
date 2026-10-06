package com.gbravo.turnoya.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.shared.BusinessRuleException;

class ScheduleRulesTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Duration HALF_HOUR = Duration.ofMinutes(30);

    private final Business business = new Business("Test", "test", ZoneId.of("America/Argentina/Cordoba"),
            LocalTime.of(9, 0), LocalTime.of(12, 0), 30, NOW);

    @Test
    void generaTurnosDentroDelHorarioEnLaZonaDelNegocio() {
        List<Instant> starts = ScheduleRules.candidateStarts(business, LocalDate.of(2026, 10, 6), Duration.ofHours(1));

        // 09:00, 09:30, 10:00, 10:30, 11:00 (el de 11:30 terminaría 12:30, fuera de horario)
        assertThat(starts).hasSize(5);
        assertThat(starts.getFirst()).isEqualTo(Instant.parse("2026-10-06T12:00:00Z"));
        assertThat(starts.getLast()).isEqualTo(Instant.parse("2026-10-06T14:00:00Z"));
    }

    @Test
    void aceptaUnTurnoValido() {
        assertThatCode(() -> ScheduleRules.ensureBookable(business, Instant.parse("2026-10-06T13:30:00Z"),
                HALF_HOUR, NOW, 60)).doesNotThrowAnyException();
    }

    @Test
    void rechazaHorariosPasados() {
        assertThatThrownBy(() -> ScheduleRules.ensureBookable(business, NOW.minusSeconds(60), HALF_HOUR, NOW, 60))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ya pasó");
    }

    @Test
    void rechazaTurnosQueNoCaenEnLaGrilla() {
        assertThatThrownBy(() -> ScheduleRules.ensureBookable(business, Instant.parse("2026-10-06T13:10:00Z"),
                HALF_HOUR, NOW, 60))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("cada 30 minutos");
    }

    @Test
    void rechazaTurnosQueTerminanDespuesDelCierre() {
        // 11:30 local + 1h = 12:30 > cierre 12:00
        assertThatThrownBy(() -> ScheduleRules.ensureBookable(business, Instant.parse("2026-10-06T14:30:00Z"),
                Duration.ofHours(1), NOW, 60))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("horario de atención");
    }

    @Test
    void rechazaReservasDemasiadoLejanas() {
        assertThatThrownBy(() -> ScheduleRules.ensureBookable(business, Instant.parse("2027-01-05T13:00:00Z"),
                HALF_HOUR, NOW, 60))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("60 días");
    }
}
