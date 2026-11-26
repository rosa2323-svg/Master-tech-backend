package utp.mastertech.mastertech.service;

import com.stripe.exception.ApiConnectionException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import utp.mastertech.mastertech.dto.CotizacionDTO;
import utp.mastertech.mastertech.dto.EntregaRequest;
import utp.mastertech.mastertech.dto.ItemPedidoDTO;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.PagoException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.exception.StockInsuficienteException;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.PuntoRepartoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Stripe se simula con mockStatic: no se hace ninguna llamada de red. */
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private VentaService ventaService;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PuntoRepartoRepository puntoRepartoRepository;
    @Mock
    private EnvioCotizacionService cotizacionService;

    @InjectMocks
    private PedidoService service;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173/");
    }

    private ItemPedidoDTO item(long productoId, int cantidad) {
        ItemPedidoDTO i = new ItemPedidoDTO();
        i.setProductoId(productoId);
        i.setCantidad(cantidad);
        return i;
    }

    private EntregaRequest entrega() {
        EntregaRequest e = new EntregaRequest();
        e.setDireccion("Av. Arequipa 123, Lince");
        e.setLatitud(new BigDecimal("-12.085000"));
        e.setLongitud(new BigDecimal("-77.035000"));
        return e;
    }

    private Producto producto(long id, double precio) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre("Producto " + id);
        p.setPrecio(precio);
        p.setStock(50);
        return p;
    }

    private PuntoReparto sede(long id) {
        PuntoReparto s = new PuntoReparto();
        s.setId(id);
        s.setNombre("Almacén Sur");
        return s;
    }

    private EnvioCotizacionService.Cotizacion cotizacion(String costo, boolean gratis, PuntoReparto sede) {
        TarifaEnvio t = new TarifaEnvio();
        t.setNombre("5 a 10 km");
        t.setDiasEntregaMin(1);
        t.setDiasEntregaMax(2);
        return new EnvioCotizacionService.Cotizacion(true, null, new BigDecimal(costo), gratis,
                new BigDecimal("7.35"), sede, t,
                new EnvioCotizacionService.VentanaEntrega(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2)));
    }

    /** Prepara el carrito [producto 1 x2 a S/100] y ejecuta el checkout con Stripe simulado. */
    private SessionCreateParams checkoutConCotizacion(EnvioCotizacionService.Cotizacion c, BigDecimal[] subtotalVisto) {
        List<ItemPedidoDTO> items = List.of(item(1, 2));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(ventaService.validarStock(items)).thenReturn(List.of());
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
        when(cotizacionService.cotizar(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    subtotalVisto[0] = inv.getArgument(0);
                    return c;
                });
        Session sesion = mock(Session.class);
        when(sesion.getUrl()).thenReturn("https://stripe.test/pay");
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(sesion);

            assertEquals("https://stripe.test/pay", service.crearCheckout("ana@x.com", items, entrega()));

            stripe.verify(() -> Session.create(params.capture()));
        }
        return params.getValue();
    }

    private Session sesionPagada(String email, String items) {
        Session s = mock(Session.class);
        when(s.getPaymentStatus()).thenReturn("paid");
        when(s.getMetadata()).thenReturn(Map.of("email", email, "items", items));
        return s;
    }

    // ---------- crearCheckout ----------

    @Test
    void crearCheckoutConStockInsuficienteLanzaExcepcionAntesDeLlamarAStripe() {
        List<ItemPedidoDTO> items = List.of(item(1, 5));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(ventaService.validarStock(items)).thenReturn(List.of("Solo hay 1 unidades disponibles de \"Mouse\"."));

        assertThrows(StockInsuficienteException.class, () -> service.crearCheckout("ana@x.com", items, entrega()));

        verifyNoInteractions(productoRepository);
    }

    @Test
    void crearCheckoutConDemasiadosProductosDistintosLanzaIllegalArgument() {
        List<ItemPedidoDTO> muchos = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            muchos.add(item(1_000_000L + i, 1));   // "1000000:1," ocupa 10 caracteres
        }
        when(ventaService.consolidar(muchos)).thenReturn(muchos);
        when(ventaService.validarStock(muchos)).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.crearCheckout("ana@x.com", muchos, entrega()));
    }

    @Test
    void crearCheckoutSinCoberturaLanzaIllegalArgumentYNoLlamaAStripe() {
        List<ItemPedidoDTO> items = List.of(item(1, 1));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(ventaService.validarStock(items)).thenReturn(List.of());
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
        when(cotizacionService.cotizar(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(Instant.class)))
                .thenReturn(EnvioCotizacionService.Cotizacion.sinCobertura("Todavía no llegamos a esa dirección."));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.crearCheckout("ana@x.com", items, entrega()));

        assertEquals("Todavía no llegamos a esa dirección.", ex.getMessage());
    }

    @Test
    void crearCheckoutAgregaElEnvioComoLineaYGuardaLaEntregaEnLaMetadata() {
        BigDecimal[] subtotal = new BigDecimal[1];

        SessionCreateParams params = checkoutConCotizacion(cotizacion("12.00", false, sede(3)), subtotal);

        assertEquals(0, new BigDecimal("200").compareTo(subtotal[0]), "el envío se cotiza sobre el subtotal de productos");
        assertEquals(2, params.getLineItems().size());
        assertEquals(10000L, params.getLineItems().get(0).getPriceData().getUnitAmount());
        assertEquals(1200L, params.getLineItems().get(1).getPriceData().getUnitAmount());
        assertEquals("Envío a domicilio", params.getLineItems().get(1).getPriceData().getProductData().getName());
        assertEquals("pen", params.getLineItems().get(1).getPriceData().getCurrency());
        Map<String, String> m = params.getMetadata();
        assertEquals("DOMICILIO", m.get("tipo"));
        assertEquals("12.00", m.get("envio"));
        assertEquals("7.35", m.get("km"));
        assertEquals("3", m.get("sede"));
        assertEquals("1:2", m.get("plazo"));
        assertEquals("Av. Arequipa 123, Lince", m.get("dir"));
        assertEquals("-12.085000", m.get("lat"));
        assertEquals("-77.035000", m.get("lng"));
    }

    @Test
    void crearCheckoutConEnvioGratisNoAgregaLineaDeEnvio() {
        SessionCreateParams params = checkoutConCotizacion(cotizacion("0.00", true, sede(3)), new BigDecimal[1]);

        assertEquals(1, params.getLineItems().size());
        assertEquals("0.00", params.getMetadata().get("envio"));
    }

    // ---------- cotizar ----------

    @Test
    void cotizarCalculaElSubtotalConPreciosDeLaBdYArmaLaRespuesta() {
        List<ItemPedidoDTO> items = List.of(item(1, 2));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
        BigDecimal[] visto = new BigDecimal[1];
        when(cotizacionService.cotizar(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(Instant.class)))
                .thenAnswer(inv -> {
                    visto[0] = inv.getArgument(0);
                    return cotizacion("12.00", false, sede(3));
                });

        CotizacionDTO dto = service.cotizar(items, new BigDecimal("-12.08"), new BigDecimal("-77.03"));

        assertEquals(0, new BigDecimal("200.00").compareTo(visto[0]));
        assertTrue(dto.isCubierto());
        assertEquals(0, new BigDecimal("200.00").compareTo(dto.getSubtotal()));
        assertEquals(0, new BigDecimal("12.00").compareTo(dto.getCostoEnvio()));
        assertEquals(0, new BigDecimal("212.00").compareTo(dto.getTotal()));
        assertFalse(dto.isEnvioGratis());
        assertEquals("Almacén Sur", dto.getSedeNombre());
        assertEquals("5 a 10 km", dto.getTarifaNombre());
        assertEquals(LocalDate.of(2026, 12, 1), dto.getEntregaDesde());
        assertEquals(LocalDate.of(2026, 12, 2), dto.getEntregaHasta());
    }

    @Test
    void cotizarSinCoberturaSoloDevuelveElMensaje() {
        List<ItemPedidoDTO> items = List.of(item(1, 1));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
        when(cotizacionService.cotizar(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(Instant.class)))
                .thenReturn(EnvioCotizacionService.Cotizacion.sinCobertura("Todavía no llegamos a esa dirección."));

        CotizacionDTO dto = service.cotizar(items, BigDecimal.ONE, BigDecimal.ONE);

        assertFalse(dto.isCubierto());
        assertEquals("Todavía no llegamos a esa dirección.", dto.getMensaje());
        assertNull(dto.getCostoEnvio());
    }

    @Test
    void cotizarConProductoInexistenteLanzaNotFound() {
        List<ItemPedidoDTO> items = List.of(item(9, 1));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cotizar(items, BigDecimal.ONE, BigDecimal.ONE));
    }

    // ---------- confirmar ----------

    @Test
    void confirmarFallaSiStripeNoResponde() {
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1"))
                    .thenThrow(new ApiConnectionException("sin red", new RuntimeException()));

            assertThrows(PagoException.class, () -> service.confirmar("ana@x.com", "cs_1"));
        }
    }

    @Test
    void confirmarRechazaUnPagoNoCompletado() {
        Session pendiente = mock(Session.class);
        when(pendiente.getPaymentStatus()).thenReturn("unpaid");
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(pendiente);

            ConflictException ex = assertThrows(ConflictException.class, () -> service.confirmar("ana@x.com", "cs_1"));

            assertTrue(ex.getMessage().contains("todavía no figura como completado"));
        }
        verifyNoInteractions(ventaService);
    }

    @Test
    void confirmarRechazaUnaSesionDeOtraCuenta() {
        Session ajena = sesionPagada("otro@x.com", "1:2");
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(ajena);

            ConflictException ex = assertThrows(ConflictException.class, () -> service.confirmar("ana@x.com", "cs_1"));

            assertTrue(ex.getMessage().contains("otra cuenta"));
        }
        verifyNoInteractions(ventaService);
    }

    @Test
    void confirmarFallaSiElUsuarioNoExiste() {
        Session s = sesionPagada("ana@x.com", "1:2");
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.empty());
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);

            assertThrows(ResourceNotFoundException.class, () -> service.confirmar("ana@x.com", "cs_1"));
        }
    }

    @Test
    void confirmarRegistraLaVentaConLosItemsDeLaMetadata() {
        Usuario usuario = new Usuario();
        Venta venta = new Venta();
        Session s = sesionPagada("ANA@x.com", "12:2,15:1");
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull())).thenReturn(venta);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);

            Venta resultado = service.confirmar("ana@x.com", "cs_1");

            assertSame(venta, resultado);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemPedidoDTO>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> orden = ArgumentCaptor.forClass(String.class);
        verify(ventaService).procesarVenta(items.capture(), eq(usuario), orden.capture(), isNull());

        assertEquals(2, items.getValue().size());
        assertEquals(12L, items.getValue().get(0).getProductoId());
        assertEquals(2, items.getValue().get(0).getCantidad());
        assertEquals(15L, items.getValue().get(1).getProductoId());
        assertEquals(1, items.getValue().get(1).getCantidad());
        assertTrue(orden.getValue().matches("MT-[0-9A-Z]{7}"), "número de orden: " + orden.getValue());
        assertEquals(10, orden.getValue().length());
    }

    @Test
    void elNumeroDeOrdenEsDeterministaPorSesion() {
        Usuario usuario = new Usuario();
        Session s = sesionPagada("ana@x.com", "1:1");
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull())).thenReturn(new Venta());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_misma")).thenReturn(s);
            service.confirmar("ana@x.com", "cs_misma");
            service.confirmar("ana@x.com", "cs_misma");
        }

        ArgumentCaptor<String> orden = ArgumentCaptor.forClass(String.class);
        verify(ventaService, times(2)).procesarVenta(anyList(), eq(usuario), orden.capture(), isNull());
        assertEquals(orden.getAllValues().get(0), orden.getAllValues().get(1));
    }

    @Test
    void confirmarCopiaLaEntregaDeLaMetadataALaVenta() {
        Usuario usuario = new Usuario();
        PuntoReparto sede = sede(3);
        Session s = mock(Session.class);
        when(s.getPaymentStatus()).thenReturn("paid");
        when(s.getMetadata()).thenReturn(Map.of(
                "email", "ana@x.com", "items", "1:2", "tipo", "DOMICILIO",
                "dir", "Av. Arequipa 123, Lince", "lat", "-12.085000", "lng", "-77.035000",
                "envio", "12.00", "km", "7.35", "sede", "3", "plazo", "1:2"));
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(puntoRepartoRepository.findById(3L)).thenReturn(Optional.of(sede));
        when(cotizacionService.estimarEntrega(eq(1), eq(2), any(Instant.class)))
                .thenReturn(new EnvioCotizacionService.VentanaEntrega(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 2)));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), any(EntregaPedido.class))).thenReturn(new Venta());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);
            service.confirmar("ana@x.com", "cs_1");
        }

        ArgumentCaptor<EntregaPedido> entrega = ArgumentCaptor.forClass(EntregaPedido.class);
        verify(ventaService).procesarVenta(anyList(), eq(usuario), anyString(), entrega.capture());
        EntregaPedido e = entrega.getValue();
        assertEquals("DOMICILIO", e.tipo());
        assertEquals(0, new BigDecimal("12.00").compareTo(e.costoEnvio()));
        assertEquals("Av. Arequipa 123, Lince", e.direccion());
        assertEquals(0, new BigDecimal("-12.085").compareTo(e.latitud()));
        assertEquals(0, new BigDecimal("7.35").compareTo(e.distanciaKm()));
        assertSame(sede, e.sede());
        assertEquals(LocalDate.of(2026, 12, 2), e.fechaEstimada());
    }
}
