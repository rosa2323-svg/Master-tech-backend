package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** El cliente solicita el reembolso de un pedido; un administrador la aprueba o rechaza. */
@Entity
@Table(name = "solicitudes_reembolso")
@Data
public class SolicitudReembolso {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    /** Quien solicita (el dueño del pedido). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    /** Administrador que resolvió la solicitud. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resuelto_por")
    private Usuario resueltoPor;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @Column(name = "nota_admin", length = 255)
    private String notaAdmin;
}
