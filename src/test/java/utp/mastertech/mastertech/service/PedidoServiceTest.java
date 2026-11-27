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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import utp.mastertech.mastertech.dto.CheckoutEmbebidoDTO;
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

    @Mock
    private StripeReembolsoClient stripeReembolso;

    @InjectMocks
    private PedidoService service;

    @BeforeEach
    void configurar() {
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:5173/");
        ReflectionTestUtils.setField(service, "stripePublishableKey", "pk_test_123");
        ReflectionTestUtils.setField(service, "expiracionMinutos", 30);
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
        when(sesion.getClientSecret()).thenReturn("cs_secret_abc");
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(sesion);

            CheckoutEmbebidoDTO dto = service.crearCheckout("ana@x.com", items, "DOMICILIO", entrega(), null);
            assertEquals("cs_secret_abc", dto.clientSecret());
            assertEquals("pk_test_123", dto.publishableKey());

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

        assertThrows(StockInsuficienteException.class, () -> service.crearCheckout("ana@x.com", items, "DOMICILIO", entrega(), null));

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

        assertThrows(IllegalArgumentException.class, () -> service.crearCheckout("ana@x.com", muchos, "DOMICILIO", entrega(), null));
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
                () -> service.crearCheckout("ana@x.com", items, "DOMICILIO", entrega(), null));

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

        CotizacionDTO dto = service.cotizar(items, "DOMICILIO", new BigDecimal("-12.08"), new BigDecimal("-77.03"), null);

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

        CotizacionDTO dto = service.cotizar(items, "DOMICILIO", BigDecimal.ONE, BigDecimal.ONE, null);

        assertFalse(dto.isCubierto());
        assertEquals("Todavía no llegamos a esa dirección.", dto.getMensaje());
        assertNull(dto.getCostoEnvio());
    }

    @Test
    void cotizarConProductoInexistenteLanzaNotFound() {
        List<ItemPedidoDTO> items = List.of(item(9, 1));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cotizar(items, "DOMICILIO", BigDecimal.ONE, BigDecimal.ONE, null));
    }

    // ---------- recojo en tienda ----------

    private PuntoReparto sedeRecojo(long id, boolean activo, boolean permiteRecojo) {
        PuntoReparto s = sede(id);
        s.setDireccion("Av. Huaylas 1200, Chorrillos");
        s.setLatitud(new BigDecimal("-12.170000"));
        s.setLongitud(new BigDecimal("-77.010000"));
        s.setActivo(activo);
        s.setPermiteRecojo(permiteRecojo);
        return s;
    }

    private void carritoConUnProducto() {
        List<ItemPedidoDTO> items = List.of(item(1, 2));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(ventaService.validarStock(items)).thenReturn(List.of());
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
    }

    @Test
    void crearCheckoutParaRecojoNoCobraEnvioNiCotiza() {
        carritoConUnProducto();
        when(puntoRepartoRepository.findById(3L)).thenReturn(Optional.of(sedeRecojo(3, true, true)));
        Session sesion = mock(Session.class);
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(sesion);

            service.crearCheckout("ana@x.com", List.of(item(1, 2)), "RECOJO", null, 3L);

            stripe.verify(() -> Session.create(params.capture()));
        }

        assertEquals(1, params.getValue().getLineItems().size(), "solo el producto: el recojo no cobra envío");
        Map<String, String> m = params.getValue().getMetadata();
        assertEquals("RECOJO", m.get("tipo"));
        assertEquals("Av. Huaylas 1200, Chorrillos", m.get("dir"));
        assertEquals("0.00", m.get("envio"));
        assertEquals("3", m.get("sede"));
        assertEquals("0:0", m.get("plazo"));
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void crearCheckoutParaRecojoSinSedeLanzaIllegalArgument() {
        carritoConUnProducto();

        assertThrows(IllegalArgumentException.class,
                () -> service.crearCheckout("ana@x.com", List.of(item(1, 2)), "RECOJO", null, null));
    }

    @Test
    void crearCheckoutParaRecojoRechazaSedesInexistentesInactivasOSinRecojo() {
        carritoConUnProducto();
        when(puntoRepartoRepository.findById(1L)).thenReturn(Optional.empty());
        when(puntoRepartoRepository.findById(2L)).thenReturn(Optional.of(sedeRecojo(2, false, true)));
        when(puntoRepartoRepository.findById(3L)).thenReturn(Optional.of(sedeRecojo(3, true, false)));
        List<ItemPedidoDTO> items = List.of(item(1, 2));

        for (long id : new long[]{1, 2, 3}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.crearCheckout("ana@x.com", items, "RECOJO", null, id));
            assertEquals("Ese punto de recojo no está disponible.", ex.getMessage());
        }
    }

    @Test
    void crearCheckoutADomicilioSinDireccionLanzaIllegalArgument() {
        carritoConUnProducto();

        assertThrows(IllegalArgumentException.class,
                () -> service.crearCheckout("ana@x.com", List.of(item(1, 2)), "DOMICILIO", null, null));
        verifyNoInteractions(cotizacionService);
    }

    @Test
    void cotizarRecojoEsGratisYListoDesdeLaFechaDePreparacion() {
        List<ItemPedidoDTO> items = List.of(item(1, 2));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));
        when(puntoRepartoRepository.findById(3L)).thenReturn(Optional.of(sedeRecojo(3, true, true)));
        when(cotizacionService.estimarEntrega(eq(0), eq(0), any(Instant.class)))
                .thenReturn(new EnvioCotizacionService.VentanaEntrega(LocalDate.of(2026, 11, 26), LocalDate.of(2026, 11, 26)));

        CotizacionDTO dto = service.cotizar(items, "RECOJO", null, null, 3L);

        assertTrue(dto.isCubierto());
        assertEquals("RECOJO", dto.getTipoEntrega());
        assertEquals(0, BigDecimal.ZERO.compareTo(dto.getCostoEnvio()));
        assertEquals(0, new BigDecimal("200.00").compareTo(dto.getTotal()));
        assertTrue(dto.isEnvioGratis());
        assertEquals("Av. Huaylas 1200, Chorrillos", dto.getSedeDireccion());
        assertEquals(LocalDate.of(2026, 11, 26), dto.getEntregaDesde());
        verify(cotizacionService, never()).cotizar(any(BigDecimal.class), any(BigDecimal.class), any(BigDecimal.class), any(Instant.class));
    }

    @Test
    void cotizarADomicilioSinUbicacionLanzaIllegalArgument() {
        List<ItemPedidoDTO> items = List.of(item(1, 1));
        when(ventaService.consolidar(items)).thenReturn(items);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, 100.0)));

        assertThrows(IllegalArgumentException.class, () -> service.cotizar(items, "DOMICILIO", null, null, null));
    }

    @Test
    void puntosRecojoSoloIncluyeSedesActivasQueLoPermiten() {
        PuntoReparto conRecojo = sedeRecojo(1, true, true);
        PuntoReparto sinRecojo = sedeRecojo(2, true, false);
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(conRecojo, sinRecojo));

        assertEquals(List.of(conRecojo), service.puntosRecojo());
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
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any())).thenReturn(venta);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);

            Venta resultado = service.confirmar("ana@x.com", "cs_1");

            assertSame(venta, resultado);
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ItemPedidoDTO>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> orden = ArgumentCaptor.forClass(String.class);
        verify(ventaService).procesarVenta(items.capture(), eq(usuario), orden.capture(), isNull(), any());

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
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any())).thenReturn(new Venta());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_misma")).thenReturn(s);
            service.confirmar("ana@x.com", "cs_misma");
            service.confirmar("ana@x.com", "cs_misma");
        }

        ArgumentCaptor<String> orden = ArgumentCaptor.forClass(String.class);
        verify(ventaService, times(2)).procesarVenta(anyList(), eq(usuario), orden.capture(), isNull(), any());
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
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), any(EntregaPedido.class), any())).thenReturn(new Venta());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);
            service.confirmar("ana@x.com", "cs_1");
        }

        ArgumentCaptor<EntregaPedido> entrega = ArgumentCaptor.forClass(EntregaPedido.class);
        verify(ventaService).procesarVenta(anyList(), eq(usuario), anyString(), entrega.capture(), any());
        EntregaPedido e = entrega.getValue();
        assertEquals("DOMICILIO", e.tipo());
        assertEquals(0, new BigDecimal("12.00").compareTo(e.costoEnvio()));
        assertEquals("Av. Arequipa 123, Lince", e.direccion());
        assertEquals(0, new BigDecimal("-12.085").compareTo(e.latitud()));
        assertEquals(0, new BigDecimal("7.35").compareTo(e.distanciaKm()));
        assertSame(sede, e.sede());
        assertEquals(LocalDate.of(2026, 12, 2), e.fechaEstimada());
    }

    // ---------- checkout incrustado y tiempo límite ----------

    @Test
    void elCheckoutEsIncrustadoYTieneTiempoLimite() {
        long antes = Instant.now().getEpochSecond();
        SessionCreateParams p = checkoutConCotizacion(cotizacion("12.00", false, sede(3)), new BigDecimal[1]);
        long despues = Instant.now().getEpochSecond();

        assertEquals(SessionCreateParams.UiMode.EMBEDDED, p.getUiMode());
        assertEquals("http://localhost:5173/pago/confirmar?session_id={CHECKOUT_SESSION_ID}", p.getReturnUrl());
        assertNull(p.getSuccessUrl());
        // 30 min + 1 min de margen: Stripe exige al menos 30 min desde que recibe la petición
        assertTrue(p.getExpiresAt() >= antes + 31 * 60 && p.getExpiresAt() <= despues + 31 * 60);
    }

    @Test
    void laExpiracionSeLimitaAlRangoQueAceptaStripe() {
        ReflectionTestUtils.setField(service, "expiracionMinutos", 5);
        long minimo = Instant.now().getEpochSecond() + 30 * 60;
        assertTrue(checkoutConCotizacion(cotizacion("12.00", false, sede(3)), new BigDecimal[1]).getExpiresAt() >= minimo);
    }

    @Test
    void sinClavePublicaDeStripeNoSeCreaLaSesion() {
        ReflectionTestUtils.setField(service, "stripePublishableKey", "");
        carritoConUnProducto();
        when(puntoRepartoRepository.findById(3L)).thenReturn(Optional.of(sedeRecojo(3, true, true)));

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            assertThrows(PagoException.class, () -> service.crearCheckout("ana@x.com", List.of(item(1, 2)), "RECOJO", null, 3L));
            stripe.verifyNoInteractions();
        }
    }

    // ---------- webhook / registro idempotente ----------

    @Test
    void elWebhookRegistraLaVentaDeUnaSesionPagada() {
        Usuario usuario = new Usuario();
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any())).thenReturn(new Venta());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(sesionPagada("ana@x.com", "1:2"));
            service.registrarPagoDeSesion("cs_1");
        }

        verify(ventaService).procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any());
    }

    @Test
    void elWebhookIgnoraUnaSesionNoPagada() {
        Session pendiente = mock(Session.class);
        when(pendiente.getPaymentStatus()).thenReturn("unpaid");

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(pendiente);
            service.registrarPagoDeSesion("cs_1");
        }

        verifyNoInteractions(ventaService);
    }

    @Test
    void elWebhookIgnoraUnaSesionAjenaALaTienda() {
        Session ajena = mock(Session.class);
        when(ajena.getPaymentStatus()).thenReturn("paid");
        when(ajena.getMetadata()).thenReturn(Map.of());

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(ajena);
            service.registrarPagoDeSesion("cs_1");
        }

        verifyNoInteractions(ventaService);
    }

    @Test
    void siConfirmarYElWebhookChocanSeDevuelveLaVentaYaRegistrada() {
        Usuario usuario = new Usuario();
        Venta existente = new Venta();
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any()))
                .thenThrow(new DataIntegrityViolationException("numero_orden duplicado"))
                .thenReturn(existente);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(sesionPagada("ana@x.com", "1:2"));
            assertSame(existente, service.confirmar("ana@x.com", "cs_1"));
        }
    }

    @Test
    void sinStockTrasPagarSeReembolsaYElWebhookNoReintenta() {
        Usuario usuario = new Usuario();
        Session s = sesionPagada("ana@x.com", "1:2");
        when(s.getPaymentIntent()).thenReturn("pi_9");
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any()))
                .thenThrow(new StockInsuficienteException("Sin stock"));

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);
            service.registrarPagoDeSesion("cs_1"); // no lanza: Stripe recibe 200
        }

        verify(stripeReembolso).reembolsar("pi_9", "reembolso-sin-stock-cs_1");
    }

    @Test
    void sinStockTrasPagarConfirmarAvisaAlClienteQueSeLeDevolvioElPago() {
        Usuario usuario = new Usuario();
        Session s = sesionPagada("ana@x.com", "1:2");
        when(s.getPaymentIntent()).thenReturn("pi_9");
        when(usuarioRepository.findByEmail("ana@x.com")).thenReturn(Optional.of(usuario));
        when(ventaService.procesarVenta(anyList(), eq(usuario), anyString(), isNull(), any()))
                .thenThrow(new StockInsuficienteException("Sin stock"));

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.retrieve("cs_1")).thenReturn(s);
            ConflictException ex = assertThrows(ConflictException.class, () -> service.confirmar("ana@x.com", "cs_1"));
            assertTrue(ex.getMessage().contains("devolvimos el pago"));
        }
        verify(stripeReembolso).reembolsar("pi_9", "reembolso-sin-stock-cs_1");
    }
}
