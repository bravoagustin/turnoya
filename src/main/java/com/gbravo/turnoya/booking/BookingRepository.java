package com.gbravo.turnoya.booking;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /** ¿Hay otra reserva que ocupe la agenda en [startAt, endAt)? Misma regla que {@link Booking#blocksSchedule}. */
    @Query("""
            select count(b) > 0 from Booking b
            where b.business.id = :businessId
              and b.id <> :excludeId
              and b.startAt < :endAt and b.endAt > :startAt
              and (b.status = com.gbravo.turnoya.booking.BookingStatus.CONFIRMED
                   or (b.status = com.gbravo.turnoya.booking.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now))
            """)
    boolean existsBlockingOverlap(@Param("businessId") UUID businessId, @Param("excludeId") UUID excludeId,
            @Param("startAt") Instant startAt, @Param("endAt") Instant endAt, @Param("now") Instant now);

    @Query("""
            select b from Booking b
            where b.business.id = :businessId
              and b.startAt < :to and b.endAt > :from
              and (b.status = com.gbravo.turnoya.booking.BookingStatus.CONFIRMED
                   or (b.status = com.gbravo.turnoya.booking.BookingStatus.PENDING_PAYMENT and b.holdExpiresAt > :now))
            """)
    List<Booking> findBlocking(@Param("businessId") UUID businessId, @Param("from") Instant from,
            @Param("to") Instant to, @Param("now") Instant now);

    /** Agenda del negocio: todas las reservas que empiezan en [from, to), con el servicio en la misma consulta. */
    @Query("""
            select b from Booking b join fetch b.service
            where b.business.id = :businessId and b.startAt >= :from and b.startAt < :to
            order by b.startAt
            """)
    List<Booking> findAgenda(@Param("businessId") UUID businessId, @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select b.id from Booking b
            where b.status = com.gbravo.turnoya.booking.BookingStatus.PENDING_PAYMENT
              and b.holdExpiresAt <= :now
            order by b.holdExpiresAt
            """)
    List<UUID> findExpiredHoldIds(@Param("now") Instant now, Pageable page);
}
