package com.gbravo.turnoya.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.gbravo.turnoya.shared.TurnoYaProperties;

/**
 * Verifica el header x-signature de los webhooks de Mercado Pago.
 * Formato: "ts=1704908010,v1=&lt;hmac-sha256 hex&gt;", firmado sobre el manifest
 * "id:{data.id};request-id:{x-request-id};ts:{ts};" con la clave secreta del webhook.
 */
@Component
public class MercadoPagoSignatureVerifier {

    private static final String HMAC = "HmacSHA256";

    private final byte[] secret;

    public MercadoPagoSignatureVerifier(TurnoYaProperties properties) {
        this.secret = properties.mercadopago().webhookSecret().getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(String signatureHeader, String requestId, String dataId) {
        if (signatureHeader == null || dataId == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals("ts")) {
                ts = kv[1];
            } else if (kv.length == 2 && kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        String expected = sign(manifest(dataId, requestId, ts));
        // Comparación en tiempo constante para no filtrar información por timing.
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    String sign(String manifest) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el HMAC", e);
        }
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder sb = new StringBuilder("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        if (requestId != null && !requestId.isBlank()) {
            sb.append("request-id:").append(requestId).append(';');
        }
        return sb.append("ts:").append(ts).append(';').toString();
    }
}
