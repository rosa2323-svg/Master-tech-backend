package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import utp.mastertech.mastertech.dto.ItemPedidoDTO;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.exception.StockInsuficienteException;
import utp.mastertech.mastertech.model.DetalleVenta;
import utp.mastertech.mastertech.model.EnvioEvento;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.SolicitudReembolso;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.EnvioEventoRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.SolicitudReembolsoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;
import utp.mastertech.mastertech.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class VentaService {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private EnvioEventoRepository envioEventoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private SolicitudReembolsoRepository solicitudReembolsoRepository;

    public static final String EN_RUTA = "En ruta";
    public static final String ENTREGADO = "Entregado";
    public static final String ANULADA = "ANULADA";

    /** A domicilio: Pagado → Preparando → En ruta → Entregado. */
    public static final List<String> FLUJO_DOMICILIO = List.of("Pagado", "Preparando", EN_RUTA, ENTREGADO);

    /** Recojo en tienda: Pagado → Preparando → Listo para recoger → Entregado. */
    public static final List<String> FLUJO_RECOJO = List.of("Pagado", "Preparando", "Listo para recoger", ENTREGADO);

    /** Todos los estados que aparecen en el panel de despacho. */
    public static final List<String> FLUJO_DESPACHO =
            List.of("Pagado", "Preparando", EN_RUTA, "Listo para recoger", ENTREGADO);

    /** "Despachado" es el nombre anterior de "En ruta" (por si quedara algún pedido sin migrar). */
    public static String normalizarEstado(String estado) {
        return "Despachado".equals(estado) ? EN_RUTA : estado;
    }

    public static List<String> flujoDe(String tipoEntrega) {
        return "RECOJO".equals(tipoEntrega) ? FLUJO_RECOJO : FLUJO_DOMICILIO;
    }

    /** Estado al que puede pasar un pedido, o null si terminó o no está en el flujo. */
    public static String siguienteEstado(String tipoEntrega, String estado) {
        List<String> flujo = flujoDe(tipoEntrega);
        int pos = flujo.indexOf(normalizarEstado(estado));
        return pos >= 0 && pos + 1 < flujo.size() ? flujo.get(pos + 1) : null;
    }

    /** Une líneas repetidas del mismo producto sumando cantidades (conserva el orden). */
    public List<ItemPedidoDTO> consolidar(List<ItemPedidoDTO> items) {
        Map<Long, Integer> porProducto = new LinkedHashMap<>();
        for (ItemPedidoDTO item : items) {
            porProducto.merge(item.getProductoId(), item.getCantidad(), Integer::sum);
        }
        List<ItemPedidoDTO> resultado = new ArrayList<>();
        porProducto.forEach((id, cantidad) -> {
            ItemPedidoDTO dto = new ItemPedidoDTO();
            dto.setProductoId(id);
            dto.setCantidad(cantidad);
            resultado.add(dto);
        });
        return resultado;
    }

    /** Devuelve un mensaje por cada producto inexistente o sin stock suficiente. */
    public List<String> validarStock(List<ItemPedidoDTO> items) {
        List<String> errores = new ArrayList<>();
        for (ItemPedidoDTO item : items) {
            Optional<Producto> opt = productoRepository.findById(item.getProductoId());
            if (opt.isEmpty()) {
                errores.add("Uno de los productos del carrito ya no está disponible.");
                continue;
            }
            Producto p = opt.get();
            if (Boolean.FALSE.equals(p.getActivo())) {
                errores.add("\"" + p.getNombre() + "\" ya no está disponible para la venta.");
                continue;
            }
            int stockActual = p.getStock() != null ? p.getStock() : 0;
            if (item.getCantidad() > stockActual) {
                errores.add("Solo hay " + stockActual + " unidades disponibles de \"" + p.getNombre() + "\".");
            }
        }
        return errores;
    }

    /**
     * Registra la venta y descuenta el stock. Es idempotente por numeroOrden: si ya existe
     * una venta con ese número (p. ej. el usuario recarga la página de confirmación),
     * devuelve la existente sin volver a descontar stock.
     */
    @Transactional
    public Venta procesarVenta(List<ItemPedidoDTO> itemsEntrada, Usuario usuario, String numeroOrden) {
        return procesarVenta(itemsEntrada, usuario, numeroOrden, null);
    }

    /** Igual que la anterior, copiando además los datos de entrega a la venta (null = valores por defecto). */
    @Transactional
    public Venta procesarVenta(List<ItemPedidoDTO> itemsEntrada, Usuario usuario, String numeroOrden,
                               EntregaPedido entrega) {
        return procesarVenta(itemsEntrada, usuario, numeroOrden, entrega, null);
    }

    /** Igual que la anterior, guardando también el pago de Stripe (necesario para poder reembolsar). */
    @Transactional
    public Venta procesarVenta(List<ItemPedidoDTO> itemsEntrada, Usuario usuario, String numeroOrden,
                               EntregaPedido entrega, String stripePaymentIntentId) {
        Optional<Venta> existente = ventaRepository.findByNumeroOrden(numeroOrden);
        if (existente.isPresent()) {
            return existente.get();
        }

        List<ItemPedidoDTO> items = consolidar(itemsEntrada);
        List<String> errores = new ArrayList<>();
        List<DetalleVenta> detalles = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        int cantidadItems = 0;

        Venta venta = new Venta();
        for (ItemPedidoDTO item : items) {
            Optional<Producto> opt = productoRepository.findByIdForUpdate(item.getProductoId());
            if (opt.isEmpty()) {
                errores.add("Uno de los productos del carrito ya no está disponible.");
                continue;
            }
            Producto p = opt.get();
            int stockActual = p.getStock() != null ? p.getStock() : 0;
            if (item.getCantidad() > stockActual) {
                errores.add("Solo hay " + stockActual + " unidades disponibles de \"" + p.getNombre() + "\".");
                continue;
            }
            p.setStock(stockActual - item.getCantidad());
            productoRepository.save(p);

            BigDecimal precio = BigDecimal.valueOf(p.getPrecio());
            BigDecimal subtotal = precio.multiply(BigDecimal.valueOf(item.getCantidad()));

            DetalleVenta detalle = new DetalleVenta();
            detalle.setVenta(venta);
            detalle.setProducto(p);
            detalle.setCantidad(item.getCantidad());
            detalle.setPrecioUnitario(precio);
            detalle.setSubtotal(subtotal);
            detalles.add(detalle);

            total = total.add(subtotal);
            cantidadItems += item.getCantidad();
        }

        if (!errores.isEmpty()) {
            // La excepción revierte la transacción: no se descuenta stock de ningún producto.
            throw new StockInsuficienteException(errores);
        }

        venta.setFecha(LocalDateTime.now());
        venta.setTotal(total);
        venta.setCantidadItems(cantidadItems);
        venta.setNumeroOrden(numeroOrden);
        venta.setUsuario(usuario);
        venta.setDetalles(detalles);
        if (entrega != null) {
            venta.setTipoEntrega(entrega.tipo());
            venta.setCostoEnvio(entrega.costoEnvio());
            venta.setDireccionEntrega(entrega.direccion());
            venta.setLatitudEntrega(entrega.latitud());
            venta.setLongitudEntrega(entrega.longitud());
            venta.setDistanciaKm(entrega.distanciaKm());
            venta.setPuntoReparto(entrega.sede());
            venta.setFechaEstimadaEntrega(entrega.fechaEstimada());
        }
        venta.setStripePaymentIntentId(stripePaymentIntentId);
        Venta guardada = ventaRepository.save(venta);
        registrarEvento(guardada, "Pagado", null, "Pago confirmado");
        return guardada;
    }

    public List<Venta> listarVentas() {
        return ventaRepository.findAll();
    }

    public Optional<Venta> obtenerVenta(Long id) {
        return ventaRepository.findById(id);
    }

    public List<DetalleVenta> obtenerDetallesPorVenta(Long ventaId) {
        return detalleVentaRepository.findByVentaId(ventaId);
    }

    public long contarVentas() {
        return ventaRepository.count();
    }

    public List<Venta> listarParaDespacho() {
        return ventaRepository.findByEstadoInOrderByIdDesc(FLUJO_DESPACHO);
    }

    public Venta cambiarEstado(Long id, String nuevoEstado) {
        return cambiarEstado(id, nuevoEstado, null, null);
    }

    /**
     * Avanza un pedido al siguiente estado de su flujo (domicilio o recojo) y lo anota en el historial.
     * Pedir el estado en que ya está no hace nada y devuelve el pedido.
     *
     * @param nota  comentario opcional del despachador
     * @param email quién lo hizo (null = sistema)
     */
    @Transactional
    public Venta cambiarEstado(Long id, String nuevoEstado, String nota, String email) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + id));

        List<String> flujo = flujoDe(venta.getTipoEntrega());
        String estadoActual = normalizarEstado(venta.getEstado());
        int actual = flujo.indexOf(estadoActual);
        int destino = flujo.indexOf(nuevoEstado);

        if (actual < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este pedido no está en el flujo de despacho (estado actual: " + venta.getEstado() + ")");
        }
        // Idempotente: si el pedido ya está en ese estado (doble clic, dos despachadores a la vez, reintento),
        // se devuelve tal cual, sin duplicar el evento del historial. El estado final conserva su mensaje propio.
        if (destino == actual && actual + 1 < flujo.size()) {
            return venta;
        }
        if (destino != actual + 1) {
            String siguiente = actual + 1 < flujo.size() ? flujo.get(actual + 1) : null;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    siguiente == null
                            ? "El pedido ya fue entregado"
                            : "Desde " + estadoActual + " solo se puede pasar a " + siguiente);
        }

        if (solicitudReembolsoRepository.existsByVentaIdAndEstado(id, SolicitudReembolso.PENDIENTE)) {
            throw new ConflictException("Hay un reembolso pendiente: resuélvelo antes de avanzar este pedido.");
        }

        venta.setEstado(nuevoEstado);
        if (ENTREGADO.equals(nuevoEstado)) {
            venta.setFechaEntregaReal(LocalDateTime.now());
        }
        Venta guardada = ventaRepository.save(venta);
        registrarEvento(guardada, nuevoEstado,
                email == null ? null : usuarioRepository.findByEmail(email).orElse(null), nota);
        return guardada;
    }

    /** Ids de los pedidos con una solicitud de reembolso sin resolver. */
    public Set<Long> ventasConReembolsoPendiente() {
        return solicitudReembolsoRepository.findVentaIdsPendientes();
    }

    /** Historial de un pedido (vista del despachador). */
    @Transactional(readOnly = true)
    public List<EnvioEvento> listarEventos(Long ventaId) {
        if (!ventaRepository.existsById(ventaId)) {
            throw new ResourceNotFoundException("Venta no encontrada: " + ventaId);
        }
        return envioEventoRepository.findHistorial(ventaId);
    }

    /** Historial de un pedido propio del cliente; los ajenos responden como inexistentes. */
    @Transactional(readOnly = true)
    public List<EnvioEvento> listarEventosDelCliente(Long ventaId, String email) {
        Venta venta = ventaRepository.findById(ventaId)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + ventaId));
        if (venta.getUsuario() == null || email == null || !email.equalsIgnoreCase(venta.getUsuario().getEmail())) {
            throw new ResourceNotFoundException("Venta no encontrada: " + ventaId);
        }
        return envioEventoRepository.findHistorial(ventaId);
    }

    private void registrarEvento(Venta venta, String estado, Usuario usuario, String nota) {
        EnvioEvento evento = new EnvioEvento();
        evento.setVenta(venta);
        evento.setEstado(estado);
        evento.setUsuario(usuario);
        evento.setNota(nota == null || nota.isBlank() ? null : nota.trim());
        envioEventoRepository.save(evento);
    }
}
