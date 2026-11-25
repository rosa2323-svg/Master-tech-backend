package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/** Sede o almacén desde donde se reparte; la tarifa se calcula desde el más cercano al cliente. */
@Entity
@Table(name = "puntos_reparto")
@Data
public class PuntoReparto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private String direccion;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitud;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitud;

    @Column(name = "permite_recojo", nullable = false)
    private Boolean permiteRecojo = true;

    @Column(nullable = false)
    private Boolean activo = true;
}
