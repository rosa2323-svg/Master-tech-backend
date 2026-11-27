package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** Nota del administrador al aprobar (opcional) o rechazar (obligatoria) una solicitud. */
@Data
public class ResolverReembolsoRequest {
    @Size(max = 255, message = "La nota no puede exceder los 255 caracteres")
    private String nota;
}
