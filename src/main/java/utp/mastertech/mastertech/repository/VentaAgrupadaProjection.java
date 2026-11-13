package utp.mastertech.mastertech.repository;

import java.math.BigDecimal;

public interface VentaAgrupadaProjection {
    Long getId();
    String getNombre();
    Long getUnidades();
    BigDecimal getIngreso();
}