package com.gbravo.turnoya.shared;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " no encontrado: " + id);
    }
}
