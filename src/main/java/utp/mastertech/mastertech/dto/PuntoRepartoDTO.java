package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PuntoRepartoDTO {

    private Long id;

    @NotBlank(message = "El nombre del almacén no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    @NotBlank(message = "La dirección del almacén no puede estar vacía")
    @Size(max = 255, message = "La dirección no puede exceder los 255 caracteres")
    private String direccion;

    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    @Digits(integer = 2, fraction = 6, message = "La latitud admite hasta 6 decimales")
    private BigDecimal latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    @Digits(integer = 3, fraction = 6, message = "La longitud admite hasta 6 decimales")
    private BigDecimal longitud;

    /** Si se puede recoger el pedido en este almacén. Por defecto true. */
    private Boolean permiteRecojo;

    /** Por defecto true al crear. */
    private Boolean activo;
}
