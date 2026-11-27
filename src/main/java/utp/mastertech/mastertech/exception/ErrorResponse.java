package utp.mastertech.mastertech.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Cuerpo estándar de todos los errores de la API.
 * {@code fieldErrors} solo viene en errores de validación (campo -> mensaje) y
 * {@code referencia} solo en errores inesperados (500), para ubicar el detalle en los logs.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;
    private String referencia;
}
