package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CambioActivoDTO {

    @NotNull(message = "Indica si el producto debe quedar activo")
    private Boolean activo;
}
