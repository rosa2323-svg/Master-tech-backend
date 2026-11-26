package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

@Data
public class ConfigEnvioDTO {

    /** Hora de corte: los pagos posteriores cuentan desde el siguiente día hábil. Formato HH:mm. */
    @NotNull(message = "La hora de corte es obligatoria")
    private LocalTime horaCorte;

    @NotNull(message = "Los días de preparación son obligatorios")
    @Min(value = 0, message = "Los días de preparación no pueden ser negativos")
    @Max(value = 30, message = "Los días de preparación no pueden superar 30")
    private Integer diasPreparacion;

    @NotBlank(message = "La zona horaria es obligatoria")
    @Size(max = 50, message = "La zona horaria no puede exceder los 50 caracteres")
    private String zonaHoraria;
}
