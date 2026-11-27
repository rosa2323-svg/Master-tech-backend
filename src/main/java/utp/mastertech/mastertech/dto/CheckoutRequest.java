package utp.mastertech.mastertech.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequest {
    @Valid
    @NotEmpty(message = "El carrito está vacío")
    private List<ItemPedidoDTO> items;

    /** DOMICILIO (por defecto) o RECOJO. */
    @NotNull(message = "El tipo de entrega es obligatorio")
    @Pattern(regexp = "DOMICILIO|RECOJO", message = "El tipo de entrega debe ser DOMICILIO o RECOJO")
    private String tipoEntrega = "DOMICILIO";

    /** Obligatoria solo para DOMICILIO. */
    @Valid
    private EntregaRequest entrega;

    /** Sede donde se recoge; obligatoria solo para RECOJO. */
    private Long puntoRepartoId;

    @JsonIgnore
    @AssertTrue(message = "Indica la dirección de entrega")
    public boolean isEntregaCompleta() {
        return !"DOMICILIO".equals(tipoEntrega) || entrega != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Elige dónde recoger tu pedido")
    public boolean isPuntoRecojoCompleto() {
        return !"RECOJO".equals(tipoEntrega) || puntoRepartoId != null;
    }
}
