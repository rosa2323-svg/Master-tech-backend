package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Cuerpo de PATCH .../activo: activa o desactiva un registro (no se borra). */
@Data
public class ActivoRequest {

    @NotNull(message = "Indica si el registro debe quedar activo")
    private Boolean activo;
}

