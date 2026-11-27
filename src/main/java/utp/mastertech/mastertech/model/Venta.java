package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ventas")
@Data
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime fecha;

    private BigDecimal total;

    private Integer cantidadItems;

    @Column(name = "numero_orden", length = 10, unique = true)
    private String numeroOrden;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<DetalleVenta> detalles;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    private String estado = "Pagado";

    // ---- Entrega (copia propia del pedido; no depende de cambios posteriores en el perfil) ----

    /** DOMICILIO o RECOJO. */
    @Column(name = "tipo_entrega", nullable = false, length = 20)
    private String tipoEntrega = "DOMICILIO";

    @Column(name = "costo_envio", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    @Column(name = "direccion_entrega")
    private String direccionEntrega;

    @Column(name = "latitud_entrega", precision = 9, scale = 6)
    private BigDecimal latitudEntrega;

    @Column(name = "longitud_entrega", precision = 9, scale = 6)
    private BigDecimal longitudEntrega;

    @Column(name = "distancia_km", precision = 7, scale = 2)
    private BigDecimal distanciaKm;

    // EAGER a propósito: tabla pequeña y evita LazyInitializationException (open-in-view=false).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "punto_reparto_id")
    private PuntoReparto puntoReparto;

    @Column(name = "fecha_estimada_entrega")
    private LocalDate fechaEstimadaEntrega;

    @Column(name = "fecha_entrega_real")
    private LocalDateTime fechaEntregaReal;

    // ---- Pago (Stripe) y anulación ----

    /** Necesario para reembolsar. No se expone en el DTO. */
    @Column(name = "stripe_payment_intent_id", length = 100, unique = true)
    private String stripePaymentIntentId;

    @Column(name = "stripe_refund_id", length = 100)
    private String stripeRefundId;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "motivo_anulacion")
    private String motivoAnulacion;
}