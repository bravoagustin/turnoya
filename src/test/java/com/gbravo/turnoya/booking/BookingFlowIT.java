package com.gbravo.turnoya.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.gbravo.turnoya.business.ServiceOffering;
import com.gbravo.turnoya.outbox.OutboxEvent;
import com.gbravo.turnoya.outbox.OutboxRepository;
import com.gbravo.turnoya.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

class BookingFlowIT extends IntegrationTest {

    @Autowired
    private HoldExpirationJob expirationJob;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void reservaPagaLaSenaYQuedaConfirmada() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        UUID businessId = service.getBusiness().getId();

        String bookingId = reserve(businessId, service.getId(), "3518000001");

        mvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.depositAmount").value(3600.00))
                .andExpect(jsonPath("$.checkoutUrl").value("http://localhost:8080/dev/payments/" + bookingId + "/approve"));

        mvc.perform(post("/dev/payments/{id}/approve", bookingId)).andExpect(status().isOk());

        mvc.perform(get("/api/bookings/{id}", bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(outbox.findByAggregateIdOrderByCreatedAt(UUID.fromString(bookingId)))
                .extracting(OutboxEvent::getEventType)
                .containsExactly("BookingHeld", "BookingConfirmed");
    }

    @Test
    void unHorarioReservadoDesapareceDeLaDisponibilidadYNoSePuedeVolverAReservar() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        UUID businessId = service.getBusiness().getId();

        mvc.perform(get("/api/businesses/{id}/availability", businessId)
                        .param("serviceId", service.getId().toString()).param("date", "2026-10-06"))
                .andExpect(jsonPath("$", hasSize(20))) // 09:00 a 18:30, cada 30 min
                .andExpect(jsonPath("$[*].startAt", hasItem(TOMORROW_10AM.toString())));

        reserve(businessId, service.getId(), "3518000001");

        mvc.perform(get("/api/businesses/{id}/availability", businessId)
                        .param("serviceId", service.getId().toString()).param("date", "2026-10-06"))
                .andExpect(jsonPath("$", hasSize(19)))
                .andExpect(jsonPath("$[*].startAt", not(hasItem(TOMORROW_10AM.toString()))));

        mvc.perform(post("/api/businesses/{id}/bookings", businessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(service.getId(), "3518000002")))
                .andExpect(status().isConflict());
    }

    @Test
    void siLaSenaVenceElHorarioSeLibera() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        UUID businessId = service.getBusiness().getId();
        String firstBooking = reserve(businessId, service.getId(), "3518000001");

        clock.advance(Duration.ofMinutes(11));
        assertThat(expirationJob.expireDueHolds()).isGreaterThanOrEqualTo(1);

        mvc.perform(get("/api/bookings/{id}", firstBooking))
                .andExpect(jsonPath("$.status").value("EXPIRED"));
        reserve(businessId, service.getId(), "3518000002"); // ahora otro cliente puede tomarlo
    }

    @Test
    void pagoTardioConElHorarioYaTomadoQuedaParaReembolso() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        UUID businessId = service.getBusiness().getId();
        String lateBooking = reserve(businessId, service.getId(), "3518000001");

        clock.advance(Duration.ofMinutes(11));
        reserve(businessId, service.getId(), "3518000002");
        mvc.perform(post("/dev/payments/{id}/approve", lateBooking)).andExpect(status().isOk());

        mvc.perform(get("/api/bookings/{id}", lateBooking))
                .andExpect(jsonPath("$.status").value("REFUND_REQUIRED"));
    }

    @Test
    void laAgendaMuestraLasReservasDelDiaEnLaZonaDelNegocio() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        UUID businessId = service.getBusiness().getId();
        String bookingId = reserve(businessId, service.getId(), "3518000001");

        mvc.perform(get("/api/businesses/{id}/bookings", businessId).param("date", "2026-10-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].serviceName").value("Corte"))
                .andExpect(jsonPath("$[0].status").value("PENDING_PAYMENT"));
        mvc.perform(get("/api/businesses/{id}/bookings", businessId).param("date", "2026-10-07"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void validaLosDatosDeEntrada() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();

        mvc.perform(post("/api/businesses/{id}/bookings", service.getBusiness().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(service.getId(), "abc")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerPhone").exists());
    }

    private String reserve(UUID businessId, UUID serviceId, String phone) throws Exception {
        String body = mvc.perform(post("/api/businesses/{id}/bookings", businessId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(serviceId, phone)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private static String bookingJson(UUID serviceId, String phone) {
        return """
                {"serviceId":"%s","customerName":"Ana","customerPhone":"%s","startAt":"%s"}
                """.formatted(serviceId, phone, TOMORROW_10AM);
    }
}
