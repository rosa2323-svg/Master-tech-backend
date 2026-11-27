package utp.mastertech.mastertech.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prueba");

    @Test
    void recursoNoEncontradoDevuelve404ConSuMensaje() {
        ResponseEntity<ErrorResponse> r = handler.handleNotFound(new ResourceNotFoundException("Venta no encontrada: 9"), request);

        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertEquals(404, r.getBody().getStatus());
        assertEquals("Venta no encontrada: 9", r.getBody().getMessage());
        assertEquals("/api/v1/prueba", r.getBody().getPath());
        assertNotNull(r.getBody().getTimestamp());
    }

    @Test
    void argumentoInvalidoDevuelve400() {
        ResponseEntity<ErrorResponse> r = handler.handleBadRequest(new IllegalArgumentException("dato mal"), request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("dato mal", r.getBody().getMessage());
    }

    @Test
    void stockInsuficienteDevuelve409ConTodosLosErrores() {
        StockInsuficienteException ex = new StockInsuficienteException(List.of("error A", "error B"));

        ResponseEntity<ErrorResponse> r = handler.handleConflict(ex, request);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("error A | error B", r.getBody().getMessage());
    }

    @Test
    void conflictoDevuelve409() {
        ResponseEntity<ErrorResponse> r = handler.handleConflict(new ConflictException("El correo ya está registrado"), request);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertEquals("El correo ya está registrado", r.getBody().getMessage());
    }

    @Test
    void violacionDeIntegridadDevuelve409() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x", new RuntimeException("duplicado"));

        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(ex, request);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
    }

    @Test
    void llaveForaneaDevuelve409ConMensajeDeTrazabilidad() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x",
                new RuntimeException("update or delete on table \"usuarios\" violates foreign key constraint \"fk_ventas_usuario\""));

        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(ex, request);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertTrue(r.getBody().getMessage().contains("trazabilidad"));
        assertFalse(r.getBody().getMessage().contains("DNI"));
    }

    @Test
    void duplicadoDevuelve409ConMensajeDeDatosRepetidos() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("x",
                new RuntimeException("duplicate key value violates unique constraint \"usuarios_email_key\""));

        ResponseEntity<ErrorResponse> r = handler.handleIntegrity(ex, request);

        assertTrue(r.getBody().getMessage().contains("Ya existe un registro"));
    }

    @Test
    void imagenMuyGrandeDevuelve413() {
        ResponseEntity<ErrorResponse> r = handler.handleMaxUpload(request);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, r.getStatusCode());
        assertTrue(r.getBody().getMessage().contains("5 MB"));
    }

    @Test
    void metodoNoPermitidoDevuelve405() {
        ResponseEntity<ErrorResponse> r = handler.handleMethod(request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, r.getStatusCode());
    }

    @Test
    void fallosDeStripeYStorageDevuelven502() {
        ResponseEntity<ErrorResponse> pago = handler.handleExterno(new PagoException("Stripe caído", new RuntimeException()), request);
        ResponseEntity<ErrorResponse> storage = handler.handleExterno(new StorageException("bucket caído"), request);

        assertEquals(HttpStatus.BAD_GATEWAY, pago.getStatusCode());
        assertEquals(HttpStatus.BAD_GATEWAY, storage.getStatusCode());
        assertEquals("bucket caído", storage.getBody().getMessage());
    }

    @Test
    void errorInesperadoDevuelve500SinFiltrarElDetalleInterno() {
        ResponseEntity<ErrorResponse> r = handler.handleGlobal(new RuntimeException("password=secreto"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertFalse(r.getBody().getMessage().contains("secreto"));
    }

    @Test
    void responseStatusExceptionRespetaSuCodigoYMotivo() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Desde Pagado solo se puede pasar a Preparando");

        ResponseEntity<ErrorResponse> r = handler.handleResponseStatus(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals(400, r.getBody().getStatus());
        assertEquals("Desde Pagado solo se puede pasar a Preparando", r.getBody().getMessage());
    }

    @Test
    void responseStatusExceptionSinMotivoUsaElTextoDelEstado() {
        ResponseEntity<ErrorResponse> r = handler.handleResponseStatus(
                new ResponseStatusException(HttpStatus.FORBIDDEN), request);

        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("Forbidden", r.getBody().getMessage());
    }

    @Test
    void validacionDevuelve400ConMensajeYErroresPorCampo() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "dto");
        binding.addError(new FieldError("dto", "email", "El correo no es válido"));
        binding.addError(new FieldError("dto", "nombre", "El nombre es obligatorio"));
        binding.addError(new FieldError("dto", "email", "El correo es obligatorio"));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorResponse> r = handler.handleValidation(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("El correo no es válido. El nombre es obligatorio. El correo es obligatorio", r.getBody().getMessage());
        assertEquals(2, r.getBody().getFieldErrors().size());
        assertEquals("El correo no es válido", r.getBody().getFieldErrors().get("email"));
    }

    @Test
    void validacionSinMensajesUsaUnTextoPorDefecto() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(new BeanPropertyBindingResult(new Object(), "dto"));

        ResponseEntity<ErrorResponse> r = handler.handleValidation(ex, request);

        assertEquals("Hay datos inválidos en el formulario", r.getBody().getMessage());
        assertNull(r.getBody().getFieldErrors());
    }

    @Test
    void cuerpoIlegibleDevuelve400SinFiltrarElDetalleDeJackson() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "JSON parse error: Unexpected character", new RuntimeException("detalle interno"), mock(HttpInputMessage.class));

        ResponseEntity<ErrorResponse> r = handler.handleBodyIlegible(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertFalse(r.getBody().getMessage().contains("Jackson"));
        assertFalse(r.getBody().getMessage().contains("detalle interno"));
    }

    @Test
    void parametroConTipoIncorrectoDevuelve400ConElNombreDelParametro() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");

        ResponseEntity<ErrorResponse> r = handler.handleTipoIncorrecto(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertTrue(r.getBody().getMessage().contains("'id'"));
    }

    @Test
    void violacionDeRestriccionDevuelve400() {
        ConstraintViolation<?> v = mock(ConstraintViolation.class);
        when(v.getMessage()).thenReturn("debe ser mayor que 0");

        ResponseEntity<ErrorResponse> r = handler.handleConstraint(new ConstraintViolationException(Set.of(v)), request);

        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("debe ser mayor que 0", r.getBody().getMessage());
    }

    @Test
    void edicionConcurrenteDevuelve409() {
        ResponseEntity<ErrorResponse> r = handler.handleBloqueoOptimista(
                new ObjectOptimisticLockingFailureException(Object.class, 1L), request);

        assertEquals(HttpStatus.CONFLICT, r.getStatusCode());
        assertTrue(r.getBody().getMessage().contains("modificó"));
    }

    @Test
    void tipoDeContenidoNoSoportadoDevuelve415() {
        ResponseEntity<ErrorResponse> r = handler.handleMediaType(request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, r.getStatusCode());
    }

    @Test
    void excepcionesDeSeguridadNoSeConviertenEn500() {
        ResponseEntity<ErrorResponse> sinPermiso = handler.handleSinPermiso(request);
        ResponseEntity<ErrorResponse> sinSesion = handler.handleNoAutenticado(request);

        assertEquals(HttpStatus.FORBIDDEN, sinPermiso.getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, sinSesion.getStatusCode());
        assertEquals("No tienes permisos para esta acción", sinPermiso.getBody().getMessage());
    }

    @Test
    void errorInesperadoIncluyeUnaReferenciaParaBuscarloEnLosLogs() {
        ResponseEntity<ErrorResponse> r = handler.handleGlobal(new RuntimeException("boom"), request);

        assertNotNull(r.getBody().getReferencia());
        assertEquals(8, r.getBody().getReferencia().length());
        assertFalse(r.getBody().getMessage().contains(r.getBody().getReferencia()));
    }

    @Test
    void errorInesperadoGeneraReferenciasDistintas() {
        String a = handler.handleGlobal(new RuntimeException("a"), request).getBody().getReferencia();
        String b = handler.handleGlobal(new RuntimeException("b"), request).getBody().getReferencia();

        assertNotEquals(a, b);
    }
}
