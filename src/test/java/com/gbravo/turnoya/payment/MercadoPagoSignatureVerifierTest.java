package com.gbravo.turnoya.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.gbravo.turnoya.shared.TurnoYaProperties;

class MercadoPagoSignatureVerifierTest {

    private final MercadoPagoSignatureVerifier verifier = new MercadoPagoSignatureVerifier(
            new TurnoYaProperties(null, null, new TurnoYaProperties.MercadoPago(null, "secreto", null)));

    @Test
    void armaElManifestComoDocumentaMercadoPago() {
        assertThat(MercadoPagoSignatureVerifier.manifest("ABC123", "req-1", "1704908010"))
                .isEqualTo("id:abc123;request-id:req-1;ts:1704908010;");
        assertThat(MercadoPagoSignatureVerifier.manifest("123", null, "1704908010"))
                .isEqualTo("id:123;ts:1704908010;");
    }

    @Test
    void aceptaUnaFirmaValida() {
        String v1 = verifier.sign("id:123;request-id:req-1;ts:1704908010;");

        assertThat(verifier.isValid("ts=1704908010,v1=" + v1, "req-1", "123")).isTrue();
    }

    @Test
    void rechazaFirmaAlteradaOIncompleta() {
        String v1 = verifier.sign("id:123;request-id:req-1;ts:1704908010;");

        assertThat(verifier.isValid("ts=1704908010,v1=" + v1, "req-1", "999")).isFalse(); // otro pago
        assertThat(verifier.isValid("ts=1704908011,v1=" + v1, "req-1", "123")).isFalse(); // otro timestamp
        assertThat(verifier.isValid("v1=" + v1, "req-1", "123")).isFalse();
        assertThat(verifier.isValid(null, "req-1", "123")).isFalse();
    }
}
