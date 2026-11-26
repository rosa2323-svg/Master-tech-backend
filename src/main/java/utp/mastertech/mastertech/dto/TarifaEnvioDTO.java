package utp.mastertech.mastertech.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/** Tramo de distancia [desde, hasta) en km con su costo (S/) y plazo en días hábiles. */
@Data
public class TarifaEnvioDTO {

    private Long id;

    @NotBlank(message = "El nombre del tramo no puede estar vacío")
    @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
    private String nombre;

    @NotNull(message = "La distancia mínima es obligatoria")
    @DecimalMin(value = "0", message = "La distancia mínima no puede ser negativa")
    @Digits(integer = 5, fraction = 2, message = "La distancia admite hasta 2 decimales")
    private BigDecimal distanciaDesdeKm;

    @NotNull(message = "La distancia máxima es obligatoria")
    @DecimalMin(value = "0", message = "La distancia máxima no puede ser negativa")
    @Digits(integer = 5, fraction = 2, message = "La distancia admite hasta 2 decimales")
    private BigDecimal distanciaHastaKm;

    @NotNull(message = "El costo es obligatorio")
    @DecimalMin(value = "0", message = "El costo no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El costo admite hasta 2 decimales")
    private BigDecimal costo;

    /** Monto de compra desde el cual el envío es gratis; vacío = nunca es gratis. */
    @DecimalMin(value = "0", message = "El monto para envío gratis no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El monto para envío gratis admite hasta 2 decimales")
    private BigDecimal envioGratisDesde;

    @NotNull(message = "Los días mínimos de entrega son obligatorios")
    @Min(value = 0, message = "Los días mínimos no pueden ser negativos")
    @Max(value = 30, message = "Los días de entrega no pueden superar 30")
    private Integer diasEntregaMin;

    @NotNull(message = "Los días máximos de entrega son obligatorios")
    @Min(value = 0, message = "Los días máximos no pueden ser negativos")
    @Max(value = 30, message = "Los días de entrega no pueden superar 30")
    private Integer diasEntregaMax;

    /** Por defecto true al crear. */
    private Boolean activo;
}

