package com.gbravo.turnoya.business;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class ServiceOfferingTest {

    private final Business business = new Business("Test", "test", ZoneId.of("America/Argentina/Cordoba"),
            LocalTime.of(9, 0), LocalTime.of(18, 0), 30, Instant.now());

    @Test
    void calculaLaSenaRedondeandoACentavos() {
        ServiceOffering service = new ServiceOffering(business, "Corte", 30, new BigDecimal("9999.99"), 33);

        assertThat(service.depositAmount()).isEqualByComparingTo("3300.00");
    }

    @Test
    void senaDelCienPorCientoEsElPrecioCompleto() {
        ServiceOffering service = new ServiceOffering(business, "Corte", 30, new BigDecimal("12000"), 100);

        assertThat(service.depositAmount()).isEqualByComparingTo("12000.00");
    }

    @Test
    void rechazaPorcentajesInvalidos() {
        assertThatThrownBy(() -> new ServiceOffering(business, "Corte", 30, BigDecimal.TEN, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
