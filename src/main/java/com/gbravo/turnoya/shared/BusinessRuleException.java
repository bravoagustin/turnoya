package com.gbravo.turnoya.shared;

/** Violación de una regla de negocio (ej: horario fuera de la agenda). Se traduce a HTTP 422. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
