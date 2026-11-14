package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
public class IngresoPromedioDTO {
    private BigDecimal totalIngreso;
    private long ordenes;
    private long dias;
    private BigDecimal promedioDiario;
    private BigDecimal ticketPromedio;
}