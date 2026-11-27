package utp.mastertech.mastertech.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SolicitudReembolsoDTO {
    private Long id;
    private Long ventaId;
    private String numeroOrden;
    private String usuarioNombre;
    private String usuarioEmail;
    /** Estado actual del pedido (Pagado, Preparando, ANULADA…). */
    private String estadoVenta;
    private String tipoEntrega;
    /** Lo que se devuelve: productos + envío. */
    private BigDecimal monto;
    private String motivo;
    /** PENDIENTE, APROBADA o RECHAZADA. */
    private String estado;
    private LocalDateTime fechaSolicitud;
    private LocalDateTime fechaResolucion;
    private String resueltoPorNombre;
    private String notaAdmin;
}
