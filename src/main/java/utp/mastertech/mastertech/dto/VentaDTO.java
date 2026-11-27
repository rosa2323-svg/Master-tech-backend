package utp.mastertech.mastertech.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class VentaDTO {

    private Long id;

    private LocalDateTime fecha;

    private BigDecimal total;

    private Integer cantidadItems;

    private String numeroOrden;

    private String estado;

    /** Próximo estado del flujo de despacho; null si ya terminó o no está en el flujo. */
    private String siguienteEstado;

    private Long usuarioId;

    private String usuarioNombre;

    private String usuarioEmail;

    private String usuarioDireccion;

    private String usuarioTelefono;

    private String tipoEntrega;

    private BigDecimal costoEnvio;

    private String direccionEntrega;

    private BigDecimal latitudEntrega;

    private BigDecimal longitudEntrega;

    private BigDecimal distanciaKm;

    private Long puntoRepartoId;

    private String puntoRepartoNombre;

    private LocalDate fechaEstimadaEntrega;

    private LocalDateTime fechaEntregaReal;

    private LocalDateTime fechaAnulacion;

    private String motivoAnulacion;

    private List<DetalleVentaDTO> detalles;
}
