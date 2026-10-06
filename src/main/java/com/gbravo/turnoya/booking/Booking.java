package com.gbravo.turnoya.booking;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.gbravo.turnoya.business.Business;
import com.gbravo.turnoya.business.ServiceOffering;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "booking")
public class Booking {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id")
    private ServiceOffering service;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "deposit_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "hold_expires_at", nullable = false)
    private Instant holdExpiresAt;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "checkout_url")
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Lock optimista: si el job de vencimiento y el webhook de pago tocan la misma reserva, uno reintenta. */
    @Version
    private long version;

    protected Booking() {
    }

    /** Reserva el horario por un tiempo limitado mientras el cliente paga la seña. */
    public static Booking hold(ServiceOffering service, String customerName, String customerPhone, Instant startAt,
            Instant now, Duration holdTtl) {
        Booking booking = new Booking();
        booking.id = UUID.randomUUID();
        booking.business = service.getBusiness();
        booking.service = service;
        booking.customerName = customerName;
        booking.customerPhone = customerPhone;
        booking.startAt = startAt;
        booking.endAt = startAt.plus(service.duration());
        booking.status = BookingStatus.PENDING_PAYMENT;
        booking.depositAmount = service.depositAmount();
        booking.holdExpiresAt = now.plus(holdTtl);
        booking.createdAt = now;
        booking.updatedAt = now;
        return booking;
    }

    public void attachCheckout(String checkoutUrl, Instant now) {
        requireStatus(BookingStatus.PENDING_PAYMENT);
        this.checkoutUrl = checkoutUrl;
        this.updatedAt = now;
    }

    public void confirm(String paymentId, Instant now) {
        if (status != BookingStatus.PENDING_PAYMENT && status != BookingStatus.EXPIRED) {
            throw new IllegalStateException("No se puede confirmar una reserva en estado " + status);
        }
        this.status = BookingStatus.CONFIRMED;
        this.paymentId = paymentId;
        this.updatedAt = now;
    }

    /** Pagó pero el horario ya no está disponible: hay que devolverle la seña. */
    public void markRefundRequired(String paymentId, Instant now) {
        if (status == BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Una reserva confirmada no requiere reembolso");
        }
        this.status = BookingStatus.REFUND_REQUIRED;
        this.paymentId = paymentId;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        requireStatus(BookingStatus.PENDING_PAYMENT);
        if (now.isBefore(holdExpiresAt)) {
            throw new IllegalStateException("La seña todavía no venció");
        }
        this.status = BookingStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        requireStatus(BookingStatus.PENDING_PAYMENT);
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
    }

    /** ¿Este turno ocupa la agenda? Una seña vencida libera el horario aunque el job todavía no la marcó. */
    public boolean blocksSchedule(Instant now) {
        return status == BookingStatus.CONFIRMED
                || (status == BookingStatus.PENDING_PAYMENT && holdExpiresAt.isAfter(now));
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return startAt.isBefore(otherEnd) && endAt.isAfter(otherStart);
    }

    private void requireStatus(BookingStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Se esperaba estado " + expected + " pero es " + status);
        }
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public ServiceOffering getService() {
        return service;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }
}
