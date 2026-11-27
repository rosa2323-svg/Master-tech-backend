package utp.mastertech.mastertech.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.service.StripeWebhookService;

import java.util.Map;

/**
 * Webhook de Stripe. Es público (Stripe no envía JWT): la autenticidad la da la firma
 * del encabezado Stripe-Signature, verificada con STRIPE_WEBHOOK_SECRET sobre el cuerpo SIN modificar.
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeWebhookController {

    @Autowired
    private StripeWebhookService webhookService;

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, String>> recibir(@RequestBody String payload,
                                                       @RequestHeader(value = "Stripe-Signature", required = false) String firma) {
        if (!webhookService.habilitado()) {
            return ResponseEntity.status(503).body(Map.of("error", "Webhook de Stripe no configurado"));
        }
        if (firma == null || firma.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Falta la firma de Stripe"));
        }
        try {
            webhookService.procesar(payload, firma);
        } catch (StripeWebhookService.FirmaInvalidaException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("recibido", "true"));
    }
}
