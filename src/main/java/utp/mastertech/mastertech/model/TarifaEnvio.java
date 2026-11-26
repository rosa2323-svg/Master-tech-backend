package utp.mastertech.mastertech.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/** Tramo de distancia (km desde el punto de reparto más cercano) con su costo y plazo de entrega. */
@Entity
@Table(name = "tarifas_envio")
@Data
public class TarifaEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "distancia_desde_km", nullable = false, precision = 7, scale = 2)
    private BigDecimal distanciaDesdeKm;

    @Column(name = "distancia_hasta_km", nullable = false, precision = 7, scale = 2)
    private BigDecimal distanciaHastaKm;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal costo;

    /** Monto de compra desde el cual el envío es gratis; null = nunca es gratis. */
    @Column(name = "envio_gratis_desde", precision = 10, scale = 2)
    private BigDecimal envioGratisDesde;

    @Column(name = "dias_entrega_min", nullable = false)
    private Integer diasEntregaMin = 1;

    @Column(name = "dias_entrega_max", nullable = false)
    private Integer diasEntregaMax = 1;

    @Column(nullable = false)
    private Boolean activo = true;
}
