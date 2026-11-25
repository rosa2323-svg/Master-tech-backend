package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** Una fila por cada cambio de estado de un pedido (historial / seguimiento). */
@Entity
@Table(name = "envio_eventos")
@Data
public class EnvioEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Column(nullable = false, length = 20)
    private String estado;

    @Column(nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /** Quién hizo el cambio; null cuando lo registra el sistema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 255)
    private String nota;
}
