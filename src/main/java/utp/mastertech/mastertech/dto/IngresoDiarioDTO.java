package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class IngresoDiarioDTO {
    private BigDecimal hoy;
    private BigDecimal ayer;
    private BigDecimal variacionPct;
}