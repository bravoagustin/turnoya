package com.gbravo.turnoya.business;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Un negocio que recibe reservas (peluquería, consultorio, estética...).
 * Su fila también funciona como lock de agenda: tomar una reserva bloquea el negocio
 * con SELECT ... FOR UPDATE para que dos clientes no se queden con el mismo horario.
 */
@Entity
@Table(name = "business")
public class Business {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "opening_time", nullable = false)
    private LocalTime openingTime;

    @Column(name = "closing_time", nullable = false)
    private LocalTime closingTime;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Business() {
    }

    public Business(String name, String slug, ZoneId timezone, LocalTime openingTime, LocalTime closingTime,
            int slotMinutes, Instant now) {
        if (!openingTime.isBefore(closingTime)) {
            throw new IllegalArgumentException("El horario de apertura debe ser anterior al de cierre");
        }
        this.id = UUID.randomUUID();
        this.name = name;
        this.slug = slug;
        this.timezone = timezone.getId();
        this.openingTime = openingTime;
        this.closingTime = closingTime;
        this.slotMinutes = slotMinutes;
        this.createdAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timezone);
    }

    public LocalTime getOpeningTime() {
        return openingTime;
    }

    public LocalTime getClosingTime() {
        return closingTime;
    }

    public int getSlotMinutes() {
        return slotMinutes;
    }
}
