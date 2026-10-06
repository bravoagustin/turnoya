package com.gbravo.turnoya.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MercadoPagoWebhookController {

    private final MercadoPagoSignatureVerifier verifier;
    private final PaymentNotificationService notifications;

    public MercadoPagoWebhookController(MercadoPagoSignatureVerifier verifier,
            PaymentNotificationService notifications) {
        this.verifier = verifier;
        this.notifications = notifications;
    }

    /**
     * Mercado Pago notifica con ?type=payment&data.id=123. No confiamos en el body:
     * verificamos la firma y después consultamos el estado real del pago a la API.
     */
    @PostMapping("/webhooks/mercadopago")
    ResponseEntity<Void> receive(@RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId) {
        if (!"payment".equals(type) || dataId == null) {
            return ResponseEntity.ok().build(); // otros tópicos: se ignoran, pero respondemos 200 para que no reintente
        }
        if (!verifier.isValid(signature, requestId, dataId)) {
            throw new InvalidWebhookSignatureException();
        }
        notifications.handlePaymentNotification(dataId);
        return ResponseEntity.ok().build();
    }
}
