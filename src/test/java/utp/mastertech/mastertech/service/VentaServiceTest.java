package utp.mastertech.mastertech.service;

import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.mastertech.mastertech.dto.ItemPedidoDTO;
import utp.mastertech.mastertech.exception.StockInsuficienteException;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.EnvioEventoRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;
import org.mockito.ArgumentCaptor;
import utp.mastertech.mastertech.model.EnvioEvento;
import utp.mastertech.mastertech.repository.VentaRepository;

import utp.mastertech.mastertech.model.PuntoReparto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private DetalleVentaRepository detalleVentaRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private EnvioEventoRepository envioEventoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private utp.mastertech.mastertech.repository.SolicitudReembolsoRepository solicitudReembolsoRepository;

    @InjectMocks
    private VentaService service;

    private ItemPedidoDTO item(long productoId, int cantidad) {
        ItemPedidoDTO i = new ItemPedidoDTO();
        i.setProductoId(productoId);
        i.setCantidad(cantidad);
        return i;
    }

    private Producto producto(long id, String nombre, double precio, Integer stock) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setPrecio(precio);
        p.setStock(stock);
        return p;
    }

    // ---------- consolidar ----------

    @Test
    void consolidarSumaLasLineasRepetidasYConservaElOrden() {
        List<ItemPedidoDTO> r = service.consolidar(List.of(item(1, 2), item(2, 1), item(1, 3)));

        assertEquals(2, r.size());
        assertEquals(1L, r.get(0).getProductoId());
        assertEquals(5, r.get(0).getCantidad());
        assertEquals(2L, r.get(1).getProductoId());
        assertEquals(1, r.get(1).getCantidad());
    }

    // ---------- validarStock ----------

    @Test
    void validarStockSinProblemasDevuelveListaVacia() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, "Mouse", 50, 10)));

        assertTrue(service.validarStock(List.of(item(1, 10))).isEmpty());
    }

    @Test
    void validarStockReportaProductoInexistente() {
        when(productoRepository.findById(1L)).thenReturn(Optional.empty());

        List<String> errores = service.validarStock(List.of(item(1, 1)));

        assertEquals(1, errores.size());
        assertTrue(errores.get(0).contains("ya no está disponible"));
    }

    @Test
    void validarStockRechazaProductoInhabilitado() {
        Producto inhabilitado = producto(1, "Mouse", 50, 10);
        inhabilitado.setActivo(false);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(inhabilitado));

        List<String> errores = service.validarStock(List.of(item(1, 1)));

        assertEquals(1, errores.size());
        assertTrue(errores.get(0).contains("ya no está disponible"));
        assertTrue(errores.get(0).contains("Mouse"));
    }

    @Test
    void validarStockReportaCantidadMayorAlStock() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, "Mouse", 50, 2)));

        List<String> errores = service.validarStock(List.of(item(1, 5)));

        assertEquals(1, errores.size());
        assertTrue(errores.get(0).contains("Solo hay 2 unidades"));
        assertTrue(errores.get(0).contains("Mouse"));
    }

    @Test
    void validarStockTrataStockNuloComoCero() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1, "Mouse", 50, null)));

        assertEquals(1, service.validarStock(List.of(item(1, 1))).size());
    }

    // ---------- procesarVenta ----------

    @Test
    void procesarVentaDescuentaStockYCalculaTotales() {
        Producto mouse = producto(1, "Mouse", 25.5, 10);
        Producto teclado = producto(2, "Teclado", 100.0, 4);
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mouse));
        when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(teclado));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        Usuario usuario = new Usuario();

        Venta v = service.procesarVenta(List.of(item(1, 2), item(2, 1)), usuario, "MT-ABC1234");

        assertEquals(8, mouse.getStock());
        assertEquals(3, teclado.getStock());
        assertEquals(0, new BigDecimal("151.00").compareTo(v.getTotal()));
        assertEquals(3, v.getCantidadItems());
        assertEquals("MT-ABC1234", v.getNumeroOrden());
        assertSame(usuario, v.getUsuario());
        assertEquals(2, v.getDetalles().size());
        assertNotNull(v.getFecha());
        verify(productoRepository).save(mouse);
        verify(productoRepository).save(teclado);
    }

    @Test
    void procesarVentaConEntregaLaCopiaALaVentaSinSumarElEnvioAlTotal() {
        Producto mouse = producto(1, "Mouse", 100.0, 10);
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mouse));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        PuntoReparto sede = new PuntoReparto();
        sede.setId(3L);
        EntregaPedido entrega = new EntregaPedido("DOMICILIO", new BigDecimal("12.00"), "Av. Arequipa 123",
                new BigDecimal("-12.085"), new BigDecimal("-77.035"), new BigDecimal("7.35"), sede, LocalDate.of(2026, 12, 2));

        Venta v = service.procesarVenta(List.of(item(1, 2)), new Usuario(), "MT-ABC1234", entrega);

        assertEquals(0, new BigDecimal("200.00").compareTo(v.getTotal()), "total = solo productos");
        assertEquals(0, new BigDecimal("12.00").compareTo(v.getCostoEnvio()));
        assertEquals("DOMICILIO", v.getTipoEntrega());
        assertEquals("Av. Arequipa 123", v.getDireccionEntrega());
        assertEquals(0, new BigDecimal("7.35").compareTo(v.getDistanciaKm()));
        assertSame(sede, v.getPuntoReparto());
        assertEquals(LocalDate.of(2026, 12, 2), v.getFechaEstimadaEntrega());
    }

    @Test
    void procesarVentaSinEntregaDejaLosValoresPorDefecto() {
        Producto mouse = producto(1, "Mouse", 100.0, 10);
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mouse));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        Venta v = service.procesarVenta(List.of(item(1, 1)), new Usuario(), "MT-ABC1234");

        assertEquals("DOMICILIO", v.getTipoEntrega());
        assertEquals(0, BigDecimal.ZERO.compareTo(v.getCostoEnvio()));
        assertNull(v.getFechaEstimadaEntrega());
    }

    @Test
    void procesarVentaEsIdempotentePorNumeroDeOrden() {
        Venta existente = new Venta();
        existente.setNumeroOrden("MT-ABC1234");
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.of(existente));

        Venta v = service.procesarVenta(List.of(item(1, 2)), new Usuario(), "MT-ABC1234");

        assertSame(existente, v);
        verifyNoInteractions(productoRepository);
        verify(ventaRepository, never()).save(any());
    }

    @Test
    void procesarVentaSinStockLanzaExcepcionYNoGuardaLaVenta() {
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(producto(1, "Mouse", 50, 1)));

        StockInsuficienteException ex = assertThrows(StockInsuficienteException.class,
                () -> service.procesarVenta(List.of(item(1, 3)), new Usuario(), "MT-ABC1234"));

        assertTrue(ex.getMessage().contains("Solo hay 1 unidades"));
        verify(ventaRepository, never()).save(any());
    }

    @Test
    void procesarVentaConProductoInexistenteFalla() {
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThrows(StockInsuficienteException.class,
                () -> service.procesarVenta(List.of(item(1, 1)), new Usuario(), "MT-ABC1234"));

        verify(ventaRepository, never()).save(any());
    }

    // ---------- consultas ----------

    @Test
    void listarYObtenerDelegaEnElRepositorio() {
        Venta v = new Venta();
        when(ventaRepository.findAll()).thenReturn(List.of(v));
        when(ventaRepository.findById(3L)).thenReturn(Optional.of(v));
        when(ventaRepository.count()).thenReturn(7L);

        assertEquals(1, service.listarVentas().size());
        assertTrue(service.obtenerVenta(3L).isPresent());
        assertEquals(7L, service.contarVentas());
    }

    // ---------- despacho ----------

    private Venta ventaEnEstado(String estado) {
        Venta v = new Venta();
        v.setId(1L);
        v.setEstado(estado);
        return v;
    }

    @Test
    void listarParaDespachoConsultaSoloLosEstadosDelFlujo() {
        Venta v = ventaEnEstado("Pagado");
        when(ventaRepository.findByEstadoInOrderByIdDesc(VentaService.FLUJO_DESPACHO)).thenReturn(List.of(v));

        List<Venta> r = service.listarParaDespacho();

        assertEquals(1, r.size());
        verify(ventaRepository).findByEstadoInOrderByIdDesc(List.of("Pagado", "Preparando", "En ruta", "Listo para recoger", "Entregado"));
    }

    @Test
    void cambiarEstadoAvanzaAlSiguienteEstadoDelFlujo() {
        Venta v = ventaEnEstado("Pagado");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        Venta r = service.cambiarEstado(1L, "Preparando");

        assertEquals("Preparando", r.getEstado());
        verify(ventaRepository).save(v);
    }

    @Test
    void cambiarEstadoRecorreTodoElFlujoHastaEntregado() {
        Venta v = ventaEnEstado("Pagado");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        service.cambiarEstado(1L, "Preparando");
        service.cambiarEstado(1L, "En ruta");
        Venta r = service.cambiarEstado(1L, "Entregado");

        assertEquals("Entregado", r.getEstado());
    }

    private Venta ventaDeRecojo(String estado) {
        Venta v = ventaEnEstado(estado);
        v.setTipoEntrega("RECOJO");
        return v;
    }

    @Test
    void elRecojoSigueSuPropioFlujoSinPasarPorEnRuta() {
        Venta v = ventaDeRecojo("Preparando");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        assertThrows(ResponseStatusException.class, () -> service.cambiarEstado(1L, "En ruta"));
        assertEquals("Listo para recoger", service.cambiarEstado(1L, "Listo para recoger").getEstado());
        assertEquals("Entregado", service.cambiarEstado(1L, "Entregado").getEstado());
    }

    @Test
    void unPedidoADomicilioNoPasaPorListoParaRecoger() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Preparando")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(1L, "Listo para recoger"));

        assertEquals("Desde Preparando solo se puede pasar a En ruta", ex.getReason());
    }

    @Test
    void unPedidoConElNombreAnteriorDespachadoSeTrataComoEnRuta() {
        Venta v = ventaEnEstado("Despachado");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("Entregado", service.cambiarEstado(1L, "Entregado").getEstado());
    }

    @Test
    void cambiarEstadoRegistraEnElHistorialQuienLoHizoYLaNota() {
        Venta v = ventaEnEstado("Pagado");
        Usuario despachador = new Usuario();
        despachador.setNombre("Luis");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarioRepository.findByEmail("luis@x.com")).thenReturn(Optional.of(despachador));

        service.cambiarEstado(1L, "Preparando", "  Embalado con cuidado  ", "luis@x.com");

        ArgumentCaptor<EnvioEvento> evento = ArgumentCaptor.forClass(EnvioEvento.class);
        verify(envioEventoRepository).save(evento.capture());
        assertEquals("Preparando", evento.getValue().getEstado());
        assertSame(despachador, evento.getValue().getUsuario());
        assertEquals("Embalado con cuidado", evento.getValue().getNota());
        assertSame(v, evento.getValue().getVenta());
    }

    @Test
    void cambiarEstadoSinNotaNiUsuarioDejaElEventoSinEsosDatos() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Pagado")));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        service.cambiarEstado(1L, "Preparando", "   ", null);

        ArgumentCaptor<EnvioEvento> evento = ArgumentCaptor.forClass(EnvioEvento.class);
        verify(envioEventoRepository).save(evento.capture());
        assertNull(evento.getValue().getNota());
        assertNull(evento.getValue().getUsuario());
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void unCambioInvalidoNoRegistraEventos() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Pagado")));

        assertThrows(ResponseStatusException.class, () -> service.cambiarEstado(1L, "Entregado", null, null));

        verifyNoInteractions(envioEventoRepository);
    }

    @Test
    void alEntregarSeGuardaLaFechaRealDeEntrega() {
        Venta v = ventaEnEstado("En ruta");
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(i -> i.getArgument(0));

        Venta r = service.cambiarEstado(1L, "Entregado");

        assertNotNull(r.getFechaEntregaReal());
    }

    @Test
    void siguienteEstadoDependeDelTipoDeEntrega() {
        assertEquals("En ruta", VentaService.siguienteEstado("DOMICILIO", "Preparando"));
        assertEquals("Listo para recoger", VentaService.siguienteEstado("RECOJO", "Preparando"));
        assertEquals("Entregado", VentaService.siguienteEstado("DOMICILIO", "Despachado"));
        assertNull(VentaService.siguienteEstado("DOMICILIO", "Entregado"));
        assertNull(VentaService.siguienteEstado("DOMICILIO", "ANULADA"));
    }

    @Test
    void listarEventosDeUnPedidoInexistenteLanzaNotFound() {
        when(ventaRepository.existsById(9L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.listarEventos(9L));
    }

    @Test
    void elClienteVeElHistorialDeSuPedido() {
        Usuario ana = new Usuario();
        ana.setEmail("ana@x.com");
        Venta v = ventaEnEstado("Pagado");
        v.setUsuario(ana);
        EnvioEvento e = new EnvioEvento();
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));
        when(envioEventoRepository.findHistorial(1L)).thenReturn(List.of(e));

        assertEquals(List.of(e), service.listarEventosDelCliente(1L, "ANA@x.com"));
    }

    @Test
    void elClienteNoPuedeVerElHistorialDePedidosAjenos() {
        Usuario otra = new Usuario();
        otra.setEmail("otra@x.com");
        Venta v = ventaEnEstado("Pagado");
        v.setUsuario(otra);
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(v));

        assertThrows(ResourceNotFoundException.class, () -> service.listarEventosDelCliente(1L, "ana@x.com"));
        verifyNoInteractions(envioEventoRepository);
    }

    @Test
    void procesarVentaRegistraElEventoPagado() {
        Producto mouse = producto(1, "Mouse", 100.0, 10);
        when(ventaRepository.findByNumeroOrden("MT-ABC1234")).thenReturn(Optional.empty());
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(mouse));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        service.procesarVenta(List.of(item(1, 1)), new Usuario(), "MT-ABC1234");

        ArgumentCaptor<EnvioEvento> evento = ArgumentCaptor.forClass(EnvioEvento.class);
        verify(envioEventoRepository).save(evento.capture());
        assertEquals("Pagado", evento.getValue().getEstado());
        assertNull(evento.getValue().getUsuario());
    }

    @Test
    void cambiarEstadoDeVentaInexistenteLanzaNotFound() {
        when(ventaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.cambiarEstado(99L, "Preparando"));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void cambiarEstadoSaltandoUnPasoDevuelve400() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Pagado")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(1L, "En ruta"));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Desde Pagado solo se puede pasar a Preparando", ex.getReason());
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void cambiarEstadoRetrocediendoDevuelve400() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("En ruta")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(1L, "Preparando"));

        assertEquals(400, ex.getStatusCode().value());
        assertEquals("Desde En ruta solo se puede pasar a Entregado", ex.getReason());
    }

    @Test
    void pedirElEstadoEnElQueYaEstaEsIdempotenteYNoDuplicaElHistorial() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Preparando")));

        Venta r = service.cambiarEstado(1L, "Preparando", "nota", "luis@x.com");

        assertEquals("Preparando", r.getEstado());
        verify(ventaRepository, never()).save(any(Venta.class));
        verify(envioEventoRepository, never()).save(any());
    }

    @Test
    void cambiarEstadoAUnValorDesconocidoDevuelve400() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Pagado")));

        assertThrows(ResponseStatusException.class, () -> service.cambiarEstado(1L, "Volando"));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void cambiarEstadoDeUnPedidoYaEntregadoDevuelve400() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Entregado")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(1L, "Entregado"));

        assertEquals("El pedido ya fue entregado", ex.getReason());
    }

    @Test
    void cambiarEstadoDeUnPedidoFueraDelFlujoDevuelve400() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("ANULADA")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.cambiarEstado(1L, "Preparando"));

        assertEquals(400, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("ANULADA"));
    }

    @Test
    void noSeAvanzaUnPedidoConReembolsoPendiente() {
        when(ventaRepository.findById(1L)).thenReturn(Optional.of(ventaEnEstado("Pagado")));
        when(solicitudReembolsoRepository.existsByVentaIdAndEstado(1L, "PENDIENTE")).thenReturn(true);

        assertThrows(utp.mastertech.mastertech.exception.ConflictException.class,
                () -> service.cambiarEstado(1L, "Preparando"));
        verify(ventaRepository, never()).save(any(Venta.class));
    }

    @Test
    void procesarVentaGuardaElPagoDeStripeParaPoderReembolsar() {
        Producto p = producto(1, "Mouse", 10.0, 5);
        when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(p));
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));
        Venta v = service.procesarVenta(List.of(item(1L, 1)), new Usuario(), "ORD-X", null, "pi_123");

        assertEquals("pi_123", v.getStripePaymentIntentId());
    }
}
