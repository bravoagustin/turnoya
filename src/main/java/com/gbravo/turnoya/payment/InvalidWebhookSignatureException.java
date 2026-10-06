package com.gbravo.turnoya.payment;

public class InvalidWebhookSignatureException extends RuntimeException {

    public InvalidWebhookSignatureException() {
        super("Firma del webhook inválida");
    }
}
