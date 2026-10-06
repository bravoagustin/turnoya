package com.gbravo.turnoya.booking;

import java.time.Instant;

public class SlotUnavailableException extends RuntimeException {

    public SlotUnavailableException(Instant startAt) {
        super("El horario " + startAt + " ya no está disponible");
    }
}
