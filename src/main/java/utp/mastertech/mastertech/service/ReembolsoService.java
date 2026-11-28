package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.model.DetalleVenta;
import utp.mastertech.mastertech.model.EnvioEvento;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.SolicitudReembolso;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.EnvioEventoRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.SolicitudReembolsoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;
import utp.mastertech.mastertech.repository.VentaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reembolsos: el cliente solicita; un despachador o un administrador aprueba o rechaza.
 * Solo se reembolsa el pedido completo (productos + envío) y únicamente antes de que salga a reparto
 * o quede listo para recoger. Al aprobar: se devuelve el dinero en Stripe, vuelve el stock y el pedido pasa a ANULADA.
 */
@Service
public class ReembolsoService {

    /** Estados en los que todavía se puede reembolsar. */
    static final List<String> ESTADOS_REEMBOLSABLES = List.of("Pagado", "Preparando");

    @Autowired
    private SolicitudReembolsoRepository solicitudRepository;

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private EnvioEventoRepository envioEventoRepository;

    @Autowired
    private StripeReembolsoClient stripe;

    // ---------- cliente ----------

    @Transactional
    public SolicitudReembolso solicitar(Long ventaId, String email, String motivo) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + ventaId));
        Usuario dueno = venta.getUsuario();
        if (dueno == null || email == null || !email.equalsIgnoreCase(dueno.getEmail())) {
            throw new ResourceNotFoundException("Venta no encontrada: " + ventaId); // no revelar pedidos ajenos
        }
        exigirReembolsable(venta);
        if (solicitudRepository.existsByVentaIdAndEstado(ventaId, SolicitudReembolso.PENDIENTE)) {
            throw new ConflictException("Ya hay una solicitud de reembolso pendiente para este pedido.");
        }

        SolicitudReembolso solicitud = new SolicitudReembolso();
        solicitud.setVenta(venta);
        solicitud.setUsuario(dueno);
        solicitud.setMotivo(motivo.trim());
        return solicitudRepository.save(solicitud);
    }

    @Transactional(readOnly = true)
    public List<SolicitudReembolso> listarDelCliente(String email) {
        return solicitudRepository.findDelCliente(email);
    }

    // ---------- administrador ----------

    @Transactional(readOnly = true)
    public List<SolicitudReembolso> listar(String estado) {
        return estado == null || estado.isBlank()
                ? solicitudRepository.findTodas()
                : solicitudRepository.findPorEstado(estado.trim().toUpperCase());
    }

    /**
     * Devuelve el dinero en Stripe y anula el pedido. Si Stripe falla no cambia nada. Si la base de datos
     * fallara DESPUÉS del reembolso, reintentar es seguro: la clave de idempotencia evita devolver dos veces.
     */
    @Transactional
    public SolicitudReembolso aprobar(Long id, String emailAdmin, String nota) {
        SolicitudReembolso solicitud = pendiente(id);
        Venta venta = solicitud.getVenta();
        exigirReembolsable(venta);

        String refundId = stripe.reembolsar(venta.getStripePaymentIntentId(), "reembolso-venta-" + venta.getId());

        for (DetalleVenta detalle : venta.getDetalles()) {
            if (detalle.getProducto() == null || detalle.getCantidad() == null) continue;
            Producto p = productoRepository.findByIdForUpdate(detalle.getProducto().getId()).orElse(null);
            if (p == null) continue;
            p.setStock((p.getStock() != null ? p.getStock() : 0) + detalle.getCantidad());
            productoRepository.save(p);
        }

        LocalDateTime ahora = LocalDateTime.now();
        venta.setEstado(VentaService.ANULADA);
        venta.setFechaAnulacion(ahora);
        venta.setMotivoAnulacion(recortar(solicitud.getMotivo(), 255));
        venta.setStripeRefundId(refundId);
        ventaRepository.save(venta);

        Usuario admin = buscar(emailAdmin);
        EnvioEvento evento = new EnvioEvento();
        evento.setVenta(venta);
        evento.setEstado(VentaService.ANULADA);
        evento.setUsuario(admin);
        evento.setNota("Reembolso aprobado");
        envioEventoRepository.save(evento);

        return resolver(solicitud, SolicitudReembolso.APROBADA, admin, ahora, nota);
    }

    @Transactional
    public SolicitudReembolso rechazar(Long id, String emailAdmin, String nota) {
        if (nota == null || nota.isBlank()) {
            throw new IllegalArgumentException("Explica al cliente por qué se rechaza el reembolso.");
        }
        SolicitudReembolso solicitud = pendiente(id);
        return resolver(solicitud, SolicitudReembolso.RECHAZADA, buscar(emailAdmin), LocalDateTime.now(), nota);
    }

    // ---------- internos ----------

    private SolicitudReembolso pendiente(Long id) {
        SolicitudReembolso s = solicitudRepository.findConDatos(id)
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud no encontrada: " + id));
        if (!SolicitudReembolso.PENDIENTE.equals(s.getEstado())) {
            throw new ConflictException("Esta solicitud ya fue resuelta.");
        }
        return s;
    }

    private void exigirReembolsable(Venta venta) {
        String estado = VentaService.normalizarEstado(venta.getEstado());
        if (VentaService.ANULADA.equals(estado)) {
            throw new ConflictException("Este pedido ya fue anulado.");
        }
        if (!ESTADOS_REEMBOLSABLES.contains(estado)) {
            throw new ConflictException(
                    "El reembolso solo es posible antes de que el pedido salga a reparto o esté listo para recoger.");
        }
        if (venta.getStripePaymentIntentId() == null || venta.getStripePaymentIntentId().isBlank()) {
            throw new ConflictException("Este pedido no tiene un pago de Stripe asociado que se pueda reembolsar.");
        }
    }

    private SolicitudReembolso resolver(SolicitudReembolso s, String estado, Usuario admin, LocalDateTime fecha, String nota) {
        s.setEstado(estado);
        s.setResueltoPor(admin);
        s.setFechaResolucion(fecha);
        s.setNotaAdmin(nota == null || nota.isBlank() ? null : nota.trim());
        return solicitudRepository.save(s);
    }

    private Usuario buscar(String email) {
        return email == null ? null : usuarioRepository.findByEmail(email).orElse(null);
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }
}
