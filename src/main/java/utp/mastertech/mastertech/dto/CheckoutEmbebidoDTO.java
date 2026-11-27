package utp.mastertech.mastertech.dto;

/**
 * Lo que el front necesita para montar el pago incrustado de Stripe.
 *
 * @param clientSecret   secreto de la sesión (un solo uso, solo para esa sesión)
 * @param publishableKey clave pública de Stripe (pensada para ir en el navegador)
 * @param expiraEn       instante (segundos Unix) en que la sesión deja de aceptar pagos
 */
public record CheckoutEmbebidoDTO(String clientSecret, String publishableKey, long expiraEn) {
}
