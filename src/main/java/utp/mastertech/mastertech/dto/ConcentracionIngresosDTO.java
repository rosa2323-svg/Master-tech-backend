package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ConcentracionIngresosDTO {

    private BigDecimal totalIngreso;
    private BigDecimal top5Pct;
    private List<Item> productos;
    private BigDecimal otrosIngreso;
    private BigDecimal otrosPct;

    @Data
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String nombre;
        private BigDecimal ingreso;
        private BigDecimal porcentaje;
    }
}