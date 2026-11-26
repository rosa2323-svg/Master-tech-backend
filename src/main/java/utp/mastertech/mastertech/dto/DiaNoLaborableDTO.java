package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class DiaNoLaborableDTO {

    @NotNull(message = "La fecha del feriado es obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "La descripción del feriado no puede estar vacía")
    @Size(max = 100, message = "La descripción no puede exceder los 100 caracteres")
    private String descripcion;
}
