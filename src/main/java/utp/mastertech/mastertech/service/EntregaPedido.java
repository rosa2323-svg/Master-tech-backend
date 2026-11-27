package utp.mastertech.mastertech.service;

import utp.mastertech.mastertech.model.PuntoReparto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Datos de entrega que se copian a la venta al registrarla. */
public record EntregaPedido(String tipo, BigDecimal costoEnvio, String direccion,
                            BigDecimal latitud, BigDecimal longitud, BigDecimal distanciaKm,
                            PuntoReparto sede, LocalDate fechaEstimada) {
}
