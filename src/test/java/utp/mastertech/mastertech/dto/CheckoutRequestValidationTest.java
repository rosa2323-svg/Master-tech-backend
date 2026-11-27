package utp.mastertech.mastertech.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CheckoutRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ItemPedidoDTO item(Long id, Integer cantidad) {
        ItemPedidoDTO i = new ItemPedidoDTO();
        i.setProductoId(id);
        i.setCantidad(cantidad);
        return i;
    }

    private EntregaRequest entrega() {
        EntregaRequest e = new EntregaRequest();
        e.setDireccion("Av. Arequipa 123, Lince");
        e.setLatitud(new BigDecimal("-12.085"));
        e.setLongitud(new BigDecimal("-77.035"));
        return e;
    }

    private Set<String> mensajes(CheckoutRequest r) {
        return validator.validate(r).stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void carritoConItemsValidosNoTieneErrores() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 2)));
        r.setEntrega(entrega());

        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void carritoVacioEsInvalido() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of());

        assertTrue(mensajes(r).contains("El carrito está vacío"));
    }

    @Test
    void laCantidadMinimaEs1() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 0)));

        assertTrue(mensajes(r).contains("La cantidad mínima es 1"));
    }

    @Test
    void exigeProductoYCantidad() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(null, null)));

        Set<String> m = mensajes(r);
        assertTrue(m.contains("El producto es obligatorio"));
        assertTrue(m.contains("La cantidad es obligatoria"));
    }

    @Test
    void laEntregaEsObligatoria() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 1)));

        assertTrue(mensajes(r).contains("Indica la dirección de entrega"));
    }

    @Test
    void laEntregaExigeDireccionYUbicacionValida() {
        EntregaRequest e = new EntregaRequest();
        e.setDireccion(" ");
        e.setLatitud(new BigDecimal("95"));
        e.setLongitud(null);
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 1)));
        r.setEntrega(e);

        Set<String> m = mensajes(r);
        assertTrue(m.contains("La dirección de entrega es obligatoria"));
        assertTrue(m.contains("La latitud debe estar entre -90 y 90"));
        assertTrue(m.contains("La longitud de entrega es obligatoria"));
    }

    @Test
    void elTipoDeEntregaPorDefectoEsDomicilio() {
        assertEquals("DOMICILIO", new CheckoutRequest().getTipoEntrega());
    }

    @Test
    void recojoConSedeNoNecesitaDireccion() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 1)));
        r.setTipoEntrega("RECOJO");
        r.setPuntoRepartoId(3L);

        assertTrue(validator.validate(r).isEmpty());
    }

    @Test
    void recojoSinSedeEsInvalido() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 1)));
        r.setTipoEntrega("RECOJO");

        assertTrue(mensajes(r).contains("Elige dónde recoger tu pedido"));
    }

    @Test
    void elTipoDeEntregaSoloAceptaDomicilioORecojo() {
        CheckoutRequest r = new CheckoutRequest();
        r.setItems(List.of(item(1L, 1)));
        r.setTipoEntrega("DRON");

        assertTrue(mensajes(r).contains("El tipo de entrega debe ser DOMICILIO o RECOJO"));
    }

    @Test
    void cotizarExigeUbicacionADomicilioYSedeParaRecojo() {
        CotizacionRequest dom = new CotizacionRequest();
        dom.setItems(List.of(item(1L, 1)));
        CotizacionRequest rec = new CotizacionRequest();
        rec.setItems(List.of(item(1L, 1)));
        rec.setTipoEntrega("RECOJO");

        assertTrue(validator.validate(dom).stream().anyMatch(v -> v.getMessage().equals("La ubicación de entrega es obligatoria")));
        assertTrue(validator.validate(rec).stream().anyMatch(v -> v.getMessage().equals("Elige dónde recoger tu pedido")));

        dom.setLatitud(new BigDecimal("-12.08"));
        dom.setLongitud(new BigDecimal("-77.03"));
        rec.setPuntoRepartoId(3L);
        assertTrue(validator.validate(dom).isEmpty());
        assertTrue(validator.validate(rec).isEmpty());
    }

    @Test
    void confirmarPagoExigeElIdDeSesion() {
        ConfirmarPagoRequest r = new ConfirmarPagoRequest();
        r.setSessionId(" ");

        assertFalse(validator.validate(r).isEmpty());
    }
}
