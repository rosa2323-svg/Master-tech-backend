package utp.mastertech.mastertech.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resultado de cotizar un envío. Si {@code cubierto} es false solo viene el mensaje. */
@Data
public class CotizacionDTO {
    private boolean cubierto;
    private String mensaje;
    private BigDecimal subtotal;
    private BigDecimal costoEnvio;
    /** Lo que se paga en total: productos + envío. */
    private BigDecimal total;
    private boolean envioGratis;
    private BigDecimal distanciaKm;
    private String sedeNombre;
    private String tarifaNombre;
    private LocalDate entregaDesde;
    private LocalDate entregaHasta;
}
