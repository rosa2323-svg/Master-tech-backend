package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/** Feriado o día no laborable. Domingos y estos días no cuentan como hábiles (lunes a sábado sí). */
@Entity
@Table(name = "dias_no_laborables")
@Data
public class DiaNoLaborable {

    @Id
    private LocalDate fecha;

    @Column(nullable = false, length = 100)
    private String descripcion;
}
