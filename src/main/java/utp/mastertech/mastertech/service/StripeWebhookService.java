package utp.mastertech.mastertech.service;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Avisos de Stripe (webhook). Garantizan que la venta se registre aunque el cliente cierre la pestaña
 * antes de volver a la tienda. Registrar es idempotente: el aviso y /confirmar pueden llegar los dos.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    /** Firma inválida o ausente. */
    public static class FirmaInvalidaException extends RuntimeException {
        public FirmaInvalidaException(String mensaje) {
            super(mensaje);
        }
    }

    @Autowired
    private PedidoService pedidoService;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    public boolean habilitado() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /** Verifica la firma y procesa el evento. Los eventos que no nos interesan se ignoran con éxito. */
    public void procesar(String payload, String firma) {
        Event evento;
        try {
            evento = Webhook.constructEvent(payload, firma, webhookSecret);
        } catch (SignatureVerificationException | RuntimeException e) {
            throw new FirmaInvalidaException("Firma de Stripe inválida");
        }

        switch (evento.getType()) {
            case "checkout.session.completed" -> {
                String sessionId = idDeSesion(evento);
                if (sessionId == null) {
                    log.warn("Evento {} sin sesión legible", evento.getId());
                    return;
                }
                pedidoService.registrarPagoDeSesion(sessionId);
            }
            case "checkout.session.expired" ->
                    // No hay stock reservado durante el pago: no hay nada que liberar.
                    log.info("Sesión de pago expirada sin pagar: {}", idDeSesion(evento));
            default -> log.debug("Evento de Stripe ignorado: {}", evento.getType());
        }
    }

    private String idDeSesion(Event evento) {
        EventDataObjectDeserializer d = evento.getDataObjectDeserializer();
        StripeObject objeto = d.getObject().orElse(null);
        if (objeto == null) {
            try {
                objeto = d.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                return null;
            }
        }
        return objeto instanceof Session s ? s.getId() : null;
    }
}
