package utp.mastertech.mastertech.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface TendenciaVentaProjection {
    LocalDate getPeriodo();
    Long getOrdenes();
    Long getUnidades();
    BigDecimal getIngreso();
}