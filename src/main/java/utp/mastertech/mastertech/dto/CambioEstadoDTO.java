package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CambioEstadoDTO {
    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    /** Comentario opcional que queda en el historial del pedido. */
    @Size(max = 255, message = "La nota no puede exceder los 255 caracteres")
    private String nota;
}
