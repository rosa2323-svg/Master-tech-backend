package utp.mastertech.mastertech.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Pide el costo y la fecha estimada de un carrito para una ubicación. */
@Data
public class CotizacionRequest {

    @Valid
    @NotEmpty(message = "El carrito está vacío")
    private List<ItemPedidoDTO> items;

    @NotNull(message = "La latitud de entrega es obligatoria")
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    private BigDecimal latitud;

    @NotNull(message = "La longitud de entrega es obligatoria")
    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    private BigDecimal longitud;
}
