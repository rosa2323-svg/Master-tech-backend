package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalTime;

/** Configuración de entregas: una sola fila (id = 1). */
@Entity
@Table(name = "config_envio")
@Data
public class ConfigEnvio {

    @Id
    private Short id = 1;

    /** Pagos posteriores a esta hora cuentan desde el siguiente día hábil. */
    @Column(name = "hora_corte", nullable = false)
    private LocalTime horaCorte = LocalTime.NOON;

    /** Días hábiles de preparación antes de salir a reparto (0 = sale el mismo día hábil). */
    @Column(name = "dias_preparacion", nullable = false)
    private Integer diasPreparacion = 0;

    @Column(name = "zona_horaria", nullable = false, length = 50)
    private String zonaHoraria = "America/Lima";
}
