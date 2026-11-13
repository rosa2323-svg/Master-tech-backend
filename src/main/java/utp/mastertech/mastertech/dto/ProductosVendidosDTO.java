package utp.mastertech.mastertech.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
public class ProductosVendidosDTO {

    private long totalUnidades;
    private List<Item> porCategoria;
    private List<Item> topProductos;

    @Data
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String nombre;
        private long unidades;
        private BigDecimal ingreso;
    }
}