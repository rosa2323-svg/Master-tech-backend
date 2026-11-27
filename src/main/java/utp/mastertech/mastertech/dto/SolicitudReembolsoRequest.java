package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SolicitudReembolsoRequest {
    @NotBlank(message = "Cuéntanos el motivo del reembolso")
    @Size(min = 5, max = 500, message = "El motivo debe tener entre 5 y 500 caracteres")
    private String motivo;
}
