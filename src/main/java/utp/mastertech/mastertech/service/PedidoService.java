package utp.mastertech.mastertech.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.PuntoRepartoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Checkout sin sesión de servidor: el front envía las líneas del carrito (id + cantidad),
 * el backend toma precios y stock de la BD, crea la sesión de Stripe y guarda las líneas
 * en la metadata de esa sesión. La venta se registra al volver del pago (/confirmar) o cuando Stripe avisa
 * por webhook, lo que ocurra primero: ambos caminos son idempotentes.
 */
@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    /** Stripe exige que la sesión dure entre 30 minutos y 24 horas. */
    private static final int MIN_EXPIRACION_MIN = 30;
    private static final int MAX_EXPIRACION_MIN = 1440;

    private static final int MAX_METADATA = 500; // límite de Stripe por valor de metadata
    private static final String TIPO_DOMICILIO = "DOMICILIO";
    private static final String TIPO_RECOJO = "RECOJO";

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PuntoRepartoRepository puntoRepartoRepository;

    @Autowired
    private EnvioCotizacionService cotizacionService;

    @Autowired
    private StripeReembolsoClient stripeReembolso;

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.publishable.key:}")
    private String stripePublishableKey;

    @Value("${stripe.checkout.expiracion-minutos:30}")
    private int expiracionMinutos;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
    }

    /** Crea una sesión de Checkout incrustado con tiempo límite para pagar. */
    public CheckoutEmbebidoDTO crearCheckout(String email, List<ItemPedidoDTO> itemsEntrada, String tipoEntrega,
                                EntregaRequest entrega, Long puntoRepartoId) {
        List<ItemPedidoDTO> items = ventaService.consolidar(itemsEntrada);

        List<String> errores = ventaService.validarStock(items);
        if (!errores.isEmpty()) {
            throw new StockInsuficienteException(errores);
        }

        String metadataItems = codificarItems(items);
        if (metadataItems.length() > MAX_METADATA) {
            throw new IllegalArgumentException("El carrito tiene demasiados productos distintos para pagar de una vez.");
        }

        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedidoDTO item : items) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + item.getProductoId()));
            long centimos = aCentimos(BigDecimal.valueOf(p.getPrecio()));
            subtotal = subtotal.add(BigDecimal.valueOf(p.getPrecio()).multiply(BigDecimal.valueOf(item.getCantidad())));
            lineItems.add(SessionCreateParams.LineItem.builder()
                    .setQuantity((long) item.getCantidad())
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency("pen")
                            .setUnitAmount(centimos)
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(p.getNombre())
                                    .build())
                            .build())
                    .build());
        }

        // El costo de envío SIEMPRE se calcula aquí: nunca se acepta un monto enviado por el front.
        Map<String, String> datosEntrega = new LinkedHashMap<>();
        if (TIPO_RECOJO.equals(tipoEntrega)) {
            PuntoReparto sede = puntoRecojo(puntoRepartoId);
            datosEntrega.put("tipo", TIPO_RECOJO);
            datosEntrega.put("dir", sede.getDireccion());
            datosEntrega.put("lat", sede.getLatitud().toPlainString());
            datosEntrega.put("lng", sede.getLongitud().toPlainString());
            datosEntrega.put("envio", "0.00");
            datosEntrega.put("km", "0.00");
            datosEntrega.put("sede", String.valueOf(sede.getId()));
            datosEntrega.put("plazo", "0:0");
        } else {
            if (entrega == null) {
                throw new IllegalArgumentException("Indica la dirección de entrega.");
            }
            EnvioCotizacionService.Cotizacion cotizacion = cotizacionService.cotizar(
                    subtotal, entrega.getLatitud(), entrega.getLongitud(), Instant.now());
            if (!cotizacion.cubierto()) {
                throw new IllegalArgumentException(cotizacion.mensaje());
            }
            if (cotizacion.costoEnvio().signum() > 0) {
                lineItems.add(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("pen")
                                .setUnitAmount(aCentimos(cotizacion.costoEnvio()))
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Envío a domicilio")
                                        .build())
                                .build())
                        .build());
            }
            datosEntrega.put("tipo", TIPO_DOMICILIO);
            datosEntrega.put("dir", entrega.getDireccion().trim());
            datosEntrega.put("lat", entrega.getLatitud().toPlainString());
            datosEntrega.put("lng", entrega.getLongitud().toPlainString());
            datosEntrega.put("envio", cotizacion.costoEnvio().toPlainString());
            datosEntrega.put("km", cotizacion.distanciaKm().toPlainString());
            datosEntrega.put("sede", String.valueOf(cotizacion.sede().getId()));
            datosEntrega.put("plazo", cotizacion.tarifa().getDiasEntregaMin() + ":" + cotizacion.tarifa().getDiasEntregaMax());
        }

        if (stripePublishableKey == null || stripePublishableKey.isBlank()) {
            log.error("Falta configurar STRIPE_PUBLISHABLE_KEY: no se puede crear el pago");
            throw new PagoException("El pago en línea no está disponible por el momento. Inténtalo más tarde.", null);
        }

        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        // Stripe rechaza vencimientos a menos de 30 min del momento en que recibe la petición: 1 min de margen.
        long minutos = Math.max(MIN_EXPIRACION_MIN, Math.min(MAX_EXPIRACION_MIN, expiracionMinutos));
        long expiraEn = Instant.now().plusSeconds(minutos * 60 + 60).getEpochSecond();
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setUiMode(SessionCreateParams.UiMode.EMBEDDED)
                    .setReturnUrl(base + "/pago/confirmar?session_id={CHECKOUT_SESSION_ID}")
                    .setExpiresAt(expiraEn)
                    .putMetadata("email", email)
                    .putMetadata("items", metadataItems)
                    .putAllMetadata(datosEntrega)
                    .addAllLineItem(lineItems)
                    .build();
            Session session = Session.create(params);
            return new CheckoutEmbebidoDTO(session.getClientSecret(), stripePublishableKey, expiraEn);
        } catch (StripeException e) {
            throw new PagoException("No se pudo conectar con la pasarela de pago. Inténtalo de nuevo en unos minutos.", e);
        }
    }

    /** Sedes donde el cliente puede recoger su pedido. */
    public List<PuntoReparto> puntosRecojo() {
        return puntoRepartoRepository.findByActivoTrue().stream()
                .filter(p -> Boolean.TRUE.equals(p.getPermiteRecojo()))
                .toList();
    }

    /**
     * Costo y fecha estimada de un carrito. No valida stock: eso lo hace el checkout.
     * A domicilio usa la ubicación; para recoger usa la sede (gratis, listo desde la fecha de preparación).
     */
    public CotizacionDTO cotizar(List<ItemPedidoDTO> itemsEntrada, String tipoEntrega,
                                 BigDecimal latitud, BigDecimal longitud, Long puntoRepartoId) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (ItemPedidoDTO item : ventaService.consolidar(itemsEntrada)) {
            Producto p = productoRepository.findById(item.getProductoId())
                    .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + item.getProductoId()));
            subtotal = subtotal.add(BigDecimal.valueOf(p.getPrecio()).multiply(BigDecimal.valueOf(item.getCantidad())));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

        CotizacionDTO dto = new CotizacionDTO();
        if (TIPO_RECOJO.equals(tipoEntrega)) {
            PuntoReparto sede = puntoRecojo(puntoRepartoId);
            EnvioCotizacionService.VentanaEntrega ventana = cotizacionService.estimarEntrega(0, 0, Instant.now());
            dto.setCubierto(true);
            dto.setTipoEntrega(TIPO_RECOJO);
            dto.setSubtotal(subtotal);
            dto.setCostoEnvio(BigDecimal.ZERO.setScale(2));
            dto.setTotal(subtotal);
            dto.setEnvioGratis(true);
            dto.setSedeNombre(sede.getNombre());
            dto.setSedeDireccion(sede.getDireccion());
            dto.setTarifaNombre("Recojo en tienda");
            dto.setEntregaDesde(ventana.desde());
            dto.setEntregaHasta(ventana.hasta());
            return dto;
        }

        if (latitud == null || longitud == null) {
            throw new IllegalArgumentException("Indica la ubicación de entrega.");
        }
        EnvioCotizacionService.Cotizacion c = cotizacionService.cotizar(subtotal, latitud, longitud, Instant.now());
        dto.setCubierto(c.cubierto());
        dto.setMensaje(c.mensaje());
        if (!c.cubierto()) {
            return dto;
        }
        dto.setTipoEntrega(TIPO_DOMICILIO);
        dto.setSubtotal(subtotal);
        dto.setCostoEnvio(c.costoEnvio());
        dto.setTotal(subtotal.add(c.costoEnvio()));
        dto.setEnvioGratis(c.gratis());
        dto.setDistanciaKm(c.distanciaKm());
        dto.setSedeNombre(c.sede().getNombre());
        dto.setTarifaNombre(c.tarifa().getNombre());
        dto.setEntregaDesde(c.entrega().desde());
        dto.setEntregaHasta(c.entrega().hasta());
        return dto;
    }

    private PuntoReparto puntoRecojo(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Elige dónde recoger tu pedido.");
        }
        return puntoRepartoRepository.findById(id)
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && Boolean.TRUE.equals(p.getPermiteRecojo()))
                .orElseThrow(() -> new IllegalArgumentException("Ese punto de recojo no está disponible."));
    }

    /** Verifica el pago en Stripe y registra la venta (idempotente). */
    public Venta confirmar(String email, String sessionId) {
        Session session = obtenerSesion(sessionId);

        if (!"paid".equals(session.getPaymentStatus())) {
            throw new ConflictException("El pago todavía no figura como completado.");
        }

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !email.equalsIgnoreCase(metadata.get("email"))) {
            throw new ConflictException("Esta sesión de pago pertenece a otra cuenta.");
        }
        return registrarVenta(session, sessionId, metadata, email);
    }

    /**
     * Aviso de Stripe (webhook): registra la venta de una sesión pagada aunque el cliente no haya vuelto a la tienda.
     * Sin pago completado no hace nada. Si ya estaba registrada (por /confirmar) no la duplica.
     */
    public void registrarPagoDeSesion(String sessionId) {
        Session session = obtenerSesion(sessionId);
        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Sesión {} sin pago completado: no se registra venta", sessionId);
            return;
        }
        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || metadata.get("email") == null) {
            log.warn("Sesión {} sin email en la metadata: no es un pedido de la tienda", sessionId);
            return;
        }
        try {
            registrarVenta(session, sessionId, metadata, metadata.get("email"));
        } catch (ConflictException e) {
            // Sin stock: el pago ya se devolvió. Responder con éxito para que Stripe no reintente el aviso.
            log.warn("Pedido de la sesión {} no registrado: {}", sessionId, e.getMessage());
        }
    }

    private Session obtenerSesion(String sessionId) {
        try {
            return Session.retrieve(sessionId);
        } catch (StripeException e) {
            throw new PagoException("No se pudo verificar el pago con Stripe.", e);
        }
    }

    private Venta registrarVenta(Session session, String sessionId, Map<String, String> metadata, String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + email));

        List<ItemPedidoDTO> items = decodificarItems(metadata.get("items"));
        String numeroOrden = numeroOrdenDe(sessionId);
        EntregaPedido entrega = entregaDe(metadata);
        try {
            return ventaService.procesarVenta(items, usuario, numeroOrden, entrega, session.getPaymentIntent());
        } catch (DataIntegrityViolationException e) {
            // /confirmar y el webhook llegaron a la vez y el otro registró primero: devolver esa venta.
            return ventaService.procesarVenta(items, usuario, numeroOrden, entrega, session.getPaymentIntent());
        } catch (StockInsuficienteException e) {
            // El cliente ya pagó pero el stock se agotó mientras tanto: se le devuelve el dinero completo.
            devolverPago(session, sessionId);
            throw new ConflictException("Algunos productos se agotaron mientras pagabas. Te devolvimos el pago completo.");
        }
    }

    private void devolverPago(Session session, String sessionId) {
        if (session.getPaymentIntent() == null) {
            log.error("Sesión {} pagada sin stock y sin payment_intent: devolver el pago a mano", sessionId);
            return;
        }
        stripeReembolso.reembolsar(session.getPaymentIntent(), "reembolso-sin-stock-" + sessionId);
    }

    /**
     * Reconstruye la entrega desde la metadata guardada al crear el checkout. La fecha estimada se calcula
     * con el momento del pago (para aplicar la hora de corte). Sesiones anteriores a los envíos: null.
     */
    private EntregaPedido entregaDe(Map<String, String> m) {
        if (m.get("tipo") == null) {
            return null;
        }
        int[] plazo = {1, 1};
        String textoPlazo = m.get("plazo");
        if (textoPlazo != null && textoPlazo.contains(":")) {
            String[] p = textoPlazo.split(":");
            plazo = new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        }
        LocalDate fecha = cotizacionService.estimarEntrega(plazo[0], plazo[1], Instant.now()).hasta();
        PuntoReparto sede = m.get("sede") == null ? null
                : puntoRepartoRepository.findById(Long.parseLong(m.get("sede"))).orElse(null);
        return new EntregaPedido(
                m.get("tipo"),
                new BigDecimal(m.getOrDefault("envio", "0")),
                m.get("dir"),
                new BigDecimal(m.get("lat")),
                new BigDecimal(m.get("lng")),
                new BigDecimal(m.getOrDefault("km", "0")),
                sede,
                fecha);
    }

    private long aCentimos(BigDecimal soles) {
        return soles.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    // "12:2,15:1" <-> [{productoId:12,cantidad:2},{productoId:15,cantidad:1}]
    private String codificarItems(List<ItemPedidoDTO> items) {
        StringBuilder sb = new StringBuilder();
        for (ItemPedidoDTO i : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(i.getProductoId()).append(':').append(i.getCantidad());
        }
        return sb.toString();
    }

    private List<ItemPedidoDTO> decodificarItems(String texto) {
        List<ItemPedidoDTO> items = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            throw new ConflictException("La sesión de pago no contiene productos.");
        }
        for (String par : texto.split(",")) {
            String[] partes = par.split(":");
            ItemPedidoDTO dto = new ItemPedidoDTO();
            dto.setProductoId(Long.parseLong(partes[0]));
            dto.setCantidad(Integer.parseInt(partes[1]));
            items.add(dto);
        }
        return items;
    }

    /** Número de orden determinista (MT- + 7 caracteres) derivado de la sesión de Stripe: 10 caracteres en total. */
    private String numeroOrdenDe(String sessionId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8));
            String base36 = new BigInteger(1, hash).toString(36).toUpperCase();
            return "MT-" + base36.substring(0, 7);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
