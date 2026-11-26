package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Rango de distancia [desdeKm, hastaKm) que ningún tramo activo cubre. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HuecoTarifaDTO {
    private BigDecimal desdeKm;
    private BigDecimal hastaKm;
}
