package com.gbravo.turnoya.booking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.gbravo.turnoya.booking.BookingDtos.CreateBookingRequest;
import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.business.BusinessRepository;
import com.gbravo.turnoya.business.BusinessService;
import com.gbravo.turnoya.business.ServiceOffering;
import com.gbravo.turnoya.outbox.OutboxWriter;
import com.gbravo.turnoya.payment.PaymentGateway;
import com.gbravo.turnoya.payment.PaymentGateway.CheckoutLink;
import com.gbravo.turnoya.payment.PaymentGateway.CheckoutRequest;
import com.gbravo.turnoya.payment.PaymentProviderException;
import com.gbravo.turnoya.shared.NotFoundException;
import com.gbravo.turnoya.shared.TurnoYaProperties;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final BusinessRepository businesses;
    private final BusinessService businessService;
    private final PaymentGateway paymentGateway;
    private final OutboxWriter outbox;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final TurnoYaProperties.Booking config;

    public BookingService(BookingRepository bookings, BusinessRepository businesses, BusinessService businessService,
            PaymentGateway paymentGateway, OutboxWriter outbox, PlatformTransactionManager txManager, Clock clock,
            TurnoYaProperties properties) {
        this.bookings = bookings;
        this.businesses = businesses;
        this.businessService = businessService;
        this.paymentGateway = paymentGateway;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.config = properties.booking();
    }

    /**
     * Reserva en dos pasos para no llamar a Mercado Pago con el lock tomado:
     * 1) transacción corta: lock del negocio + chequeo de solapamiento + insert del hold;
     * 2) fuera de la transacción: se crea el checkout y se guarda el link.
     */
    public Booking reserve(UUID businessId, CreateBookingRequest request) {
        HoldResult hold = tx.execute(status -> createHold(businessId, request));

        CheckoutLink link;
        try {
            link = paymentGateway.createCheckout(hold.checkout());
        } catch (RuntimeException e) {
            // Liberamos el horario en vez de dejarlo bloqueado hasta que venza la seña.
            tx.executeWithoutResult(status -> bookings.findById(hold.bookingId())
                    .ifPresent(b -> b.cancel(clock.instant())));
            throw new PaymentProviderException("No se pudo generar el link de pago", e);
        }

        return tx.execute(status -> {
            Booking booking = load(hold.bookingId());
            booking.attachCheckout(link.url(), clock.instant());
            return booking;
        });
    }

    private record HoldResult(UUID bookingId, CheckoutRequest checkout) {
    }

    private HoldResult createHold(UUID businessId, CreateBookingRequest request) {
        Business business = businesses.findByIdForUpdate(businessId)
                .orElseThrow(() -> new NotFoundException("Negocio", businessId));
        ServiceOffering service = businessService.getService(businessId, request.serviceId());
        Instant now = clock.instant();

        ScheduleRules.ensureBookable(business, request.startAt(), service.duration(), now, config.maxDaysAhead());

        Booking booking = Booking.hold(service, request.customerName(), request.customerPhone(), request.startAt(),
                now, config.holdTtl());
        if (bookings.existsBlockingOverlap(businessId, booking.getId(), booking.getStartAt(), booking.getEndAt(),
                now)) {
            throw new SlotUnavailableException(request.startAt());
        }
        bookings.save(booking);
        outbox.append("booking", booking.getId(), "BookingHeld", Map.of(
                "businessId", businessId,
                "startAt", booking.getStartAt().toString(),
                "depositAmount", booking.getDepositAmount()));

        String title = "Seña " + service.getName() + " - " + business.getName();
        return new HoldResult(booking.getId(),
                new CheckoutRequest(booking.getId(), title, booking.getDepositAmount(), booking.getHoldExpiresAt()));
    }

    /**
     * Aplica un pago aprobado. Idempotente: un webhook repetido no cambia nada.
     * Si la seña ya había vencido y otro cliente tomó el horario, la reserva queda para reembolso.
     */
    @Transactional
    public void applyApprovedPayment(UUID bookingId, String paymentId, BigDecimal amount) {
        Booking booking = load(bookingId);
        if (booking.getStatus() == BookingStatus.CONFIRMED || booking.getStatus() == BookingStatus.REFUND_REQUIRED) {
            return;
        }
        if (amount.compareTo(booking.getDepositAmount()) < 0) {
            log.warn("Pago {} de {} menor a la seña {} de la reserva {}", paymentId, amount,
                    booking.getDepositAmount(), bookingId);
            return;
        }

        UUID businessId = booking.getBusiness().getId();
        businesses.findByIdForUpdate(businessId);
        Instant now = clock.instant();

        boolean slotTaken = booking.getStatus() == BookingStatus.CANCELLED
                || bookings.existsBlockingOverlap(businessId, bookingId, booking.getStartAt(), booking.getEndAt(), now);
        if (slotTaken) {
            booking.markRefundRequired(paymentId, now);
            outbox.append("booking", bookingId, "BookingRefundRequired", Map.of("paymentId", paymentId));
        } else {
            booking.confirm(paymentId, now);
            outbox.append("booking", bookingId, "BookingConfirmed", Map.of(
                    "paymentId", paymentId,
                    "customerPhone", booking.getCustomerPhone(),
                    "startAt", booking.getStartAt().toString()));
        }
    }

    /** @return true si la reserva venció en esta llamada. */
    @Transactional
    public boolean expireHold(UUID bookingId) {
        Booking booking = load(bookingId);
        Instant now = clock.instant();
        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT || booking.getHoldExpiresAt().isAfter(now)) {
            return false;
        }
        booking.expire(now);
        outbox.append("booking", bookingId, "BookingExpired", Map.of());
        return true;
    }

    /** Reservas de un día, calculado en la zona horaria del negocio. */
    @Transactional(readOnly = true)
    public List<Booking> agenda(UUID businessId, LocalDate date) {
        Business business = businessService.get(businessId);
        Instant from = date.atStartOfDay(business.getZoneId()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(business.getZoneId()).toInstant();
        return bookings.findAgenda(businessId, from, to);
    }

    @Transactional(readOnly = true)
    public Booking get(UUID bookingId) {
        return load(bookingId);
    }

    private Booking load(UUID bookingId) {
        return bookings.findById(bookingId).orElseThrow(() -> new NotFoundException("Reserva", bookingId));
    }
}
