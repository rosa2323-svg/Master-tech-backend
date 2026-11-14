package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class TendenciaPuntoDTO {
    private LocalDate periodo;
    private String etiqueta;
    private long ordenes;
    private long unidades;
    private BigDecimal ingreso;
}