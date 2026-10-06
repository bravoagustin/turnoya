package com.gbravo.turnoya.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gbravo.turnoya.booking.BookingDtos.CreateBookingRequest;
import com.gbravo.turnoya.business.ServiceOffering;
import com.gbravo.turnoya.support.IntegrationTest;

class BookingConcurrencyIT extends IntegrationTest {

    private static final int CLIENTS = 10;

    @Autowired
    private BookingService bookingService;

    @Test
    void diezClientesPidiendoElMismoHorarioSoloUnoLoConsigue() throws Exception {
        ServiceOffering service = newBusinessWithHaircut();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(CLIENTS)) {
            for (int i = 0; i < CLIENTS; i++) {
                String phone = "35180000" + String.format("%02d", i);
                results.add(pool.submit(() -> {
                    start.await(); // todos salen a la vez
                    try {
                        bookingService.reserve(service.getBusiness().getId(),
                                new CreateBookingRequest(service.getId(), "Cliente", phone, TOMORROW_10AM));
                        return "ok";
                    } catch (SlotUnavailableException e) {
                        return "ocupado";
                    }
                }));
            }
            start.countDown();

            List<String> outcomes = new ArrayList<>();
            for (Future<String> result : results) {
                outcomes.add(result.get());
            }
            assertThat(outcomes).containsOnlyOnce("ok");
            assertThat(outcomes).filteredOn("ocupado"::equals).hasSize(CLIENTS - 1);
        }
    }
}
