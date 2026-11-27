package utp.mastertech.mastertech.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, HttpServletRequest request) {
        return responder(status, mensaje, request, null, null);
    }

    private ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, HttpServletRequest request,
                                                    Map<String, String> fieldErrors, String referencia) {
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(mensaje)
                .path(request.getRequestURI())
                .fieldErrors(fieldErrors)
                .referencia(referencia)
                .build();
        return new ResponseEntity<>(error, status);
    }

    // ---------- 400: la petición está mal formada o los datos no son válidos ----------

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String mensaje = ex instanceof IllegalArgumentException ? ex.getMessage() : "Petición inválida";
        return responder(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBodyIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Cuerpo ilegible en {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es válido o está incompleto", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTipoIncorrecto(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST,
                "El valor del parámetro '" + ex.getName() + "' no es válido", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> porCampo = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> porCampo.putIfAbsent(e.getField(), mensajeODefecto(e.getDefaultMessage())));

        String mensaje = ex.getBindingResult().getAllErrors().stream()
                .map(e -> e.getDefaultMessage())
                .filter(m -> m != null && !m.isBlank())
                .distinct()
                .collect(Collectors.joining(". "));
        if (mensaje.isBlank()) {
            mensaje = "Hay datos inválidos en el formulario";
        }
        return responder(HttpStatus.BAD_REQUEST, mensaje, request, porCampo.isEmpty() ? null : porCampo, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest request) {
        String mensaje = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .distinct()
                .collect(Collectors.joining(". "));
        return responder(HttpStatus.BAD_REQUEST,
                mensaje.isBlank() ? "Hay datos inválidos en la petición" : mensaje, request);
    }

    private static String mensajeODefecto(String mensaje) {
        return mensaje == null || mensaje.isBlank() ? "Valor no válido" : mensaje;
    }

    // ---------- 401 / 403: si una excepción de seguridad llega hasta aquí no debe convertirse en 500 ----------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleNoAutenticado(HttpServletRequest request) {
        return responder(HttpStatus.UNAUTHORIZED, "Debes iniciar sesión", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleSinPermiso(HttpServletRequest request) {
        return responder(HttpStatus.FORBIDDEN, "No tienes permisos para esta acción", request);
    }

    // ---------- 404 ----------

    @ExceptionHandler({ResourceNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ErrorResponse> handleNotFound(Exception ex, HttpServletRequest request) {
        String mensaje = ex instanceof ResourceNotFoundException ? ex.getMessage() : "Recurso no encontrado";
        return responder(HttpStatus.NOT_FOUND, mensaje, request);
    }

    // ---------- 409: conflicto con el estado actual de los datos ----------

    @ExceptionHandler({StockInsuficienteException.class, ConflictException.class})
    public ResponseEntity<ErrorResponse> handleConflict(RuntimeException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Violación de integridad: {}", ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT,
                "Ya existe un registro con esos datos (correo, DNI o teléfono) o el registro está en uso.", request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleBloqueoOptimista(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.warn("Edición concurrente en {}: {}", request.getRequestURI(), ex.getMessage());
        return responder(HttpStatus.CONFLICT,
                "Otra persona modificó este registro mientras lo editabas. Recarga e inténtalo de nuevo.", request);
    }

    // ---------- 405 / 413 / 415 ----------

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(HttpServletRequest request) {
        return responder(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el tamaño máximo permitido (5 MB).", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpServletRequest request) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaType(HttpServletRequest request) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de contenido no soportado", request);
    }

    // ---------- 502: falla un servicio externo (Stripe, Supabase, Google) ----------

    @ExceptionHandler({StorageException.class, PagoException.class})
    public ResponseEntity<ErrorResponse> handleExterno(RuntimeException ex, HttpServletRequest request) {
        log.error("Fallo en servicio externo en {}", request.getRequestURI(), ex);
        return responder(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String mensaje = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return responder(status, mensaje, request);
    }

    // ---------- 500: cualquier otra cosa. Nunca se expone el detalle interno ----------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobal(Exception ex, HttpServletRequest request) {
        if (esDesconexionDelCliente(ex)) {
            log.debug("Cliente canceló la petición a {}", request.getRequestURI());
            return null;
        }
        // La referencia sale en el log y en la respuesta: el cliente la cita y se encuentra el error exacto.
        String referencia = UUID.randomUUID().toString().substring(0, 8);
        log.error("Error inesperado [ref {}] en {} {}", referencia, request.getMethod(), request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor",
                request, null, referencia);
    }

    private boolean esDesconexionDelCliente(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.apache.catalina.connector.ClientAbortException
                    || t instanceof org.springframework.web.context.request.async.AsyncRequestNotUsableException) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }
}
