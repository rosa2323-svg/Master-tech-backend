package utp.mastertech.mastertech.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import org.springframework.stereotype.Component;
import utp.mastertech.mastertech.exception.PagoException;

/** Reembolsos en Stripe (aislado del resto para poder simularlo en pruebas). */
@Component
public class StripeReembolsoClient {

    /**
     * Reembolsa el pago completo (productos + envío).
     *
     * @param claveIdempotencia misma clave = mismo reembolso: reintentar no devuelve el dinero dos veces
     * @return id del reembolso en Stripe
     */
    public String reembolsar(String paymentIntentId, String claveIdempotencia) {
        try {
            RefundCreateParams params = RefundCreateParams.builder().setPaymentIntent(paymentIntentId).build();
            RequestOptions opciones = RequestOptions.builder().setIdempotencyKey(claveIdempotencia).build();
            return Refund.create(params, opciones).getId();
        } catch (StripeException e) {
            throw new PagoException("No se pudo reembolsar el pago en Stripe: " + e.getMessage(), e);
        }
    }
}
