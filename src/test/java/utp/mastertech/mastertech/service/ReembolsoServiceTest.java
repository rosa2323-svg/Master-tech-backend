package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.PagoException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.model.*;
import utp.mastertech.mastertech.repository.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReembolsoServiceTest {

    @Mock
    private SolicitudReembolsoRepository solicitudRepository;
    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private EnvioEventoRepository envioEventoRepository;
    @Mock
    private StripeReembolsoClient stripe;

    @InjectMocks
    private ReembolsoService service;

    private Usuario usuario(String email) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setEmail(email);
        return u;
    }

    private Venta venta(String estado, String paymentIntent) {
        Venta v = new Venta();
        v.setId(7L);
        v.setEstado(estado);
        v.setUsuario(usuario("ana@x.com"));
        v.setStripePaymentIntentId(paymentIntent);
        v.setTotal(new BigDecimal("100.00"));
        v.setCostoEnvio(new BigDecimal("10.00"));
        return v;
    }

    private SolicitudReembolso pendiente(Venta v) {
        SolicitudReembolso s = new SolicitudReembolso();
        s.setId(3L);
        s.setVenta(v);
        s.setUsuario(v.getUsuario());
        s.setMotivo("Me equivoqué de producto");
        s.setEstado(SolicitudReembolso.PENDIENTE);
        return s;
    }

    // ---------- solicitar ----------

    @Test
    void solicitarCreaUnaSolicitudPendiente() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("Pagado", "pi_1")));
        when(solicitudRepository.save(any(SolicitudReembolso.class))).thenAnswer(i -> i.getArgument(0));

        SolicitudReembolso s = service.solicitar(7L, "ANA@x.com", "  Me equivoqué  ");

        assertEquals("Me equivoqué", s.getMotivo());
        assertEquals(SolicitudReembolso.PENDIENTE, s.getEstado());
    }

    @Test
    void solicitarDePedidoAjenoEsNoEncontrado() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("Pagado", "pi_1")));

        assertThrows(ResourceNotFoundException.class, () -> service.solicitar(7L, "otro@x.com", "motivo"));
        verify(solicitudRepository, never()).save(any());
    }

    @Test
    void solicitarConElPedidoEnRutaEsConflicto() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("En ruta", "pi_1")));

        assertThrows(ConflictException.class, () -> service.solicitar(7L, "ana@x.com", "motivo"));
    }

    @Test
    void solicitarDePedidoAnuladoDiceQueYaFueAnulado() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("ANULADA", "pi_1")));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.solicitar(7L, "ana@x.com", "motivo"));
        assertEquals("Este pedido ya fue anulado.", ex.getMessage());
    }

    @Test
    void solicitarSinPagoDeStripeEsConflicto() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("Pagado", null)));

        assertThrows(ConflictException.class, () -> service.solicitar(7L, "ana@x.com", "motivo"));
    }

    @Test
    void solicitarConOtraPendienteEsConflicto() {
        when(ventaRepository.findById(7L)).thenReturn(Optional.of(venta("Preparando", "pi_1")));
        when(solicitudRepository.existsByVentaIdAndEstado(7L, "PENDIENTE")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.solicitar(7L, "ana@x.com", "motivo"));
    }

    // ---------- aprobar ----------

    @Test
    void aprobarReembolsaDevuelveStockYAnulaElPedido() {
        Venta v = venta("Pagado", "pi_1");
        Producto p = new Producto();
        p.setId(5L);
        p.setStock(2);
        DetalleVenta d = new DetalleVenta();
        d.setProducto(p);
        d.setCantidad(3);
        v.setDetalles(List.of(d));
        SolicitudReembolso s = pendiente(v);
        Usuario admin = usuario("admin@x.com");
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.of(s));
        when(stripe.reembolsar("pi_1", "reembolso-venta-7")).thenReturn("re_9");
        when(productoRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(p));
        when(usuarioRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(admin));
        when(solicitudRepository.save(any(SolicitudReembolso.class))).thenAnswer(i -> i.getArgument(0));

        SolicitudReembolso r = service.aprobar(3L, "admin@x.com", "ok");

        assertEquals(5, p.getStock());
        assertEquals("ANULADA", v.getEstado());
        assertEquals("re_9", v.getStripeRefundId());
        assertNotNull(v.getFechaAnulacion());
        assertEquals(SolicitudReembolso.APROBADA, r.getEstado());
        assertEquals(admin, r.getResueltoPor());
        assertEquals("ok", r.getNotaAdmin());
        verify(ventaRepository).save(v);
        verify(envioEventoRepository).save(any(EnvioEvento.class));
    }

    @Test
    void aprobarSiStripeFallaNoCambiaNada() {
        Venta v = venta("Pagado", "pi_1");
        SolicitudReembolso s = pendiente(v);
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.of(s));
        when(stripe.reembolsar(any(), any())).thenThrow(new PagoException("falla", new RuntimeException()));

        assertThrows(PagoException.class, () -> service.aprobar(3L, "admin@x.com", null));

        assertEquals("Pagado", v.getEstado());
        verify(ventaRepository, never()).save(any());
        verify(productoRepository, never()).save(any());
    }

    @Test
    void aprobarUnaSolicitudYaResueltaEsConflicto() {
        SolicitudReembolso s = pendiente(venta("Pagado", "pi_1"));
        s.setEstado(SolicitudReembolso.RECHAZADA);
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.of(s));

        assertThrows(ConflictException.class, () -> service.aprobar(3L, "admin@x.com", null));
        verifyNoInteractions(stripe);
    }

    @Test
    void aprobarSiElPedidoYaSalioAReparteEsConflictoYNoReembolsa() {
        SolicitudReembolso s = pendiente(venta("En ruta", "pi_1"));
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.of(s));

        assertThrows(ConflictException.class, () -> service.aprobar(3L, "admin@x.com", null));
        verifyNoInteractions(stripe);
    }

    @Test
    void aprobarSolicitudInexistenteEsNoEncontrada() {
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.aprobar(3L, "admin@x.com", null));
    }

    // ---------- rechazar ----------

    @Test
    void rechazarGuardaLaNotaYNoTocaElPedido() {
        Venta v = venta("Pagado", "pi_1");
        SolicitudReembolso s = pendiente(v);
        when(solicitudRepository.findConDatos(3L)).thenReturn(Optional.of(s));
        when(usuarioRepository.findByEmail("admin@x.com")).thenReturn(Optional.of(usuario("admin@x.com")));
        when(solicitudRepository.save(any(SolicitudReembolso.class))).thenAnswer(i -> i.getArgument(0));

        SolicitudReembolso r = service.rechazar(3L, "admin@x.com", " Fuera de política ");

        assertEquals(SolicitudReembolso.RECHAZADA, r.getEstado());
        assertEquals("Fuera de política", r.getNotaAdmin());
        assertEquals("Pagado", v.getEstado());
        verifyNoInteractions(stripe);
    }

    @Test
    void rechazarSinNotaEsError() {
        assertThrows(IllegalArgumentException.class, () -> service.rechazar(3L, "admin@x.com", "  "));
    }

    // ---------- listar ----------

    @Test
    void listarSinFiltroUsaTodasYConFiltroNormalizaElEstado() {
        service.listar(null);
        service.listar("pendiente");

        verify(solicitudRepository).findTodas();
        verify(solicitudRepository).findPorEstado("PENDIENTE");
    }
}
