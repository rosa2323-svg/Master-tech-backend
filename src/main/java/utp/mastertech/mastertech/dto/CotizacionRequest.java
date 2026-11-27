package utp.mastertech.mastertech.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Pide el costo y la fecha estimada de un carrito, a domicilio (con ubicación) o para recoger (con sede). */
@Data
public class CotizacionRequest {

    @Valid
    @NotEmpty(message = "El carrito está vacío")
    private List<ItemPedidoDTO> items;

    @NotNull(message = "El tipo de entrega es obligatorio")
    @Pattern(regexp = "DOMICILIO|RECOJO", message = "El tipo de entrega debe ser DOMICILIO o RECOJO")
    private String tipoEntrega = "DOMICILIO";

    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    private BigDecimal latitud;

    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    private BigDecimal longitud;

    private Long puntoRepartoId;

    @JsonIgnore
    @AssertTrue(message = "La ubicación de entrega es obligatoria")
    public boolean isUbicacionCompleta() {
        return !"DOMICILIO".equals(tipoEntrega) || (latitud != null && longitud != null);
    }

    @JsonIgnore
    @AssertTrue(message = "Elige dónde recoger tu pedido")
    public boolean isPuntoRecojoCompleto() {
        return !"RECOJO".equals(tipoEntrega) || puntoRepartoId != null;
    }
}
