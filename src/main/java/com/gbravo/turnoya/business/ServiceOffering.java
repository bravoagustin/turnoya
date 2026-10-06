package com.gbravo.turnoya.business;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Servicio reservable (ej: "Corte de pelo", 30 min, $12.000, seña del 30%). */
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id")
    private Business business;

    @Column(nullable = false)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "deposit_percent", nullable = false)
    private int depositPercent;

    @Column(nullable = false)
    private boolean active;

    protected ServiceOffering() {
    }

    public ServiceOffering(Business business, String name, int durationMinutes, BigDecimal price,
            int depositPercent) {
        if (depositPercent < 1 || depositPercent > 100) {
            throw new IllegalArgumentException("La seña debe estar entre 1% y 100%");
        }
        this.id = UUID.randomUUID();
        this.business = business;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.price = price.setScale(2, RoundingMode.HALF_UP);
        this.depositPercent = depositPercent;
        this.active = true;
    }

    /** Monto de la seña, redondeado a centavos. */
    public BigDecimal depositAmount() {
        return price.multiply(BigDecimal.valueOf(depositPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public Duration duration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public UUID getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public String getName() {
        return name;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getDepositPercent() {
        return depositPercent;
    }

    public boolean isActive() {
        return active;
    }
}
