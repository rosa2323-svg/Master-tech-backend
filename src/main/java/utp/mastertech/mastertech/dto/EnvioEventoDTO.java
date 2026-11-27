package utp.mastertech.mastertech.dto;

import java.time.LocalDateTime;

/** Un paso del historial de un pedido. {@code usuarioNombre} es null cuando lo registró el sistema. */
public record EnvioEventoDTO(String estado, LocalDateTime fecha, String usuarioNombre, String nota) {
}
