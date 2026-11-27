package utp.mastertech.mastertech.dto;

import java.math.BigDecimal;

/** Dirección confirmada con su ubicación exacta. */
public record LugarDTO(String direccion, BigDecimal latitud, BigDecimal longitud) {
}
