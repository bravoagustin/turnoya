package com.gbravo.turnoya.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.gbravo.turnoya.booking.Booking;
import com.gbravo.turnoya.booking.BookingDtos.CreateBookingRequest;
import com.gbravo.turnoya.booking.BookingService;
import com.gbravo.turnoya.booking.BookingStatus;
import com.gbravo.turnoya.business.ServiceOffering;
import com.gbravo.turnoya.outbox.OutboxEvent;
import com.gbravo.turnoya.outbox.OutboxRepository;
import com.gbravo.turnoya.payment.PaymentGateway.PaymentStatus;
import com.gbravo.turnoya.support.IntegrationTest;

class MercadoPagoWebhookIT extends IntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private FakePaymentGateway fakeGateway;

    @Autowired
    private MercadoPagoSignatureVerifier verifier;

    @Autowired
    private OutboxRepository outbox;

    @Test
    void webhookFirmadoConfirmaLaReservaYLosReintentosNoDuplicanNada() throws Exception {
        Booking booking = newBooking();
        String paymentId = fakeGateway.simulatePayment(booking.getId(), PaymentStatus.APPROVED);

        sendWebhook(paymentId, sign(paymentId)).andExpect(status().isOk());
        sendWebhook(paymentId, sign(paymentId)).andExpect(status().isOk()); // Mercado Pago reintenta

        assertThat(bookingService.get(booking.getId()).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(outbox.findByAggregateIdOrderByCreatedAt(booking.getId()))
                .extracting(OutboxEvent::getEventType)
                .containsExactly("BookingHeld", "BookingConfirmed");
    }

    @Test
    void webhookConFirmaInvalidaSeRechaza() throws Exception {
        Booking booking = newBooking();
        String paymentId = fakeGateway.simulatePayment(booking.getId(), PaymentStatus.APPROVED);

        sendWebhook(paymentId, "ts=1,v1=deadbeef").andExpect(status().isUnauthorized());

        assertThat(bookingService.get(booking.getId()).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void pagoRechazadoNoConfirma() throws Exception {
        Booking booking = newBooking();
        String paymentId = fakeGateway.simulatePayment(booking.getId(), PaymentStatus.REJECTED);

        sendWebhook(paymentId, sign(paymentId)).andExpect(status().isOk());

        assertThat(bookingService.get(booking.getId()).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    private Booking newBooking() {
        ServiceOffering service = newBusinessWithHaircut();
        return bookingService.reserve(service.getBusiness().getId(),
                new CreateBookingRequest(service.getId(), "Ana", "3518000001", TOMORROW_10AM));
    }

    private String sign(String paymentId) {
        String ts = "1759665600";
        return "ts=" + ts + ",v1=" + verifier.sign(MercadoPagoSignatureVerifier.manifest(paymentId, "req-1", ts));
    }

    private org.springframework.test.web.servlet.ResultActions sendWebhook(String paymentId, String signature)
            throws Exception {
        return mvc.perform(post("/webhooks/mercadopago")
                .param("type", "payment")
                .param("data.id", paymentId)
                .header("x-signature", signature)
                .header("x-request-id", "req-1"));
    }
}
