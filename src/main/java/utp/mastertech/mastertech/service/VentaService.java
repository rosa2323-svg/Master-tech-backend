package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import utp.mastertech.mastertech.dto.ItemPedidoDTO;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.exception.StockInsuficienteException;
import utp.mastertech.mastertech.model.DetalleVenta;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class VentaService {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    public static final List<String> FLUJO_DESPACHO =
            List.of("Pagado", "Preparando", "Despachado", "Entregado");

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
        return ventaRepository.save(venta);
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

    @Transactional
    public Venta cambiarEstado(Long id, String nuevoEstado) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada: " + id));

        int actual = FLUJO_DESPACHO.indexOf(venta.getEstado());
        int destino = FLUJO_DESPACHO.indexOf(nuevoEstado);

        if (actual < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Este pedido no está en el flujo de despacho (estado actual: " + venta.getEstado() + ")");
        }
        if (destino != actual + 1) {
            String siguiente = actual + 1 < FLUJO_DESPACHO.size()
                    ? FLUJO_DESPACHO.get(actual + 1) : null;
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    siguiente == null
                            ? "El pedido ya fue entregado"
                            : "Desde " + venta.getEstado() + " solo se puede pasar a " + siguiente);
        }

        venta.setEstado(nuevoEstado);
        return ventaRepository.save(venta);
    }
}
