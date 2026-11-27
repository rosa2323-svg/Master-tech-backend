package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import utp.mastertech.mastertech.dto.LugarDTO;
import utp.mastertech.mastertech.dto.SugerenciaDTO;
import utp.mastertech.mastertech.exception.ConflictException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DireccionServiceTest {

    private static final String TOKEN = "123e4567-e89b-12d3-a456-426614174000";
    private DireccionService service;

    @BeforeEach
    void preparar() {
        service = spy(new DireccionService());
        ReflectionTestUtils.setField(service, "apiKey", "clave-de-prueba");
    }

    @Test
    void sinClaveNoEstaHabilitadoYLasConsultasDanConflicto() {
        ReflectionTestUtils.setField(service, "apiKey", "");

        assertFalse(service.habilitado());
        assertThrows(ConflictException.class, () -> service.sugerir("Av. Arequipa", TOKEN));
        assertThrows(ConflictException.class, () -> service.detalle("ChIJabc", TOKEN));
    }

    @Test
    void sugerirConvierteLaRespuestaDeGoogle() {
        Map<String, Object> google = Map.of("suggestions", List.of(
                Map.of("placePrediction", Map.of("placeId", "ChIJ1", "text", Map.of("text", "Av. Arequipa 123, Lince"))),
                Map.of("queryPrediction", Map.of("text", Map.of("text", "ignorada"))),
                Map.of("placePrediction", Map.of("placeId", "ChIJ2", "text", Map.of("text", "Av. Arequipa 500, Lima")))));
        doReturn(google).when(service).autocompletar(anyMap());

        List<SugerenciaDTO> r = service.sugerir("  Av. Arequipa ", TOKEN);

        assertEquals(2, r.size());
        assertEquals(new SugerenciaDTO("ChIJ1", "Av. Arequipa 123, Lince"), r.get(0));
        assertEquals("ChIJ2", r.get(1).placeId());
    }

    @Test
    void sugerirEnviaElTextoRecortadoYLaSesion() {
        doReturn(Map.of()).when(service).autocompletar(anyMap());

        service.sugerir("  Av. Arequipa ", TOKEN);

        verify(service).autocompletar(argThat(m -> "Av. Arequipa".equals(m.get("input")) && TOKEN.equals(m.get("sessionToken"))));
    }

    @Test
    void sinSugerenciasDevuelveListaVacia() {
        doReturn(Map.of()).when(service).autocompletar(anyMap());

        assertTrue(service.sugerir("zzzzz", TOKEN).isEmpty());
    }

    @Test
    void sugerirRechazaTextosCortosYTokensInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.sugerir("ab", TOKEN));
        assertThrows(IllegalArgumentException.class, () -> service.sugerir("Av. Arequipa", "corto"));
        assertThrows(IllegalArgumentException.class, () -> service.sugerir("Av. Arequipa", "con espacios y /"));
        verify(service, never()).autocompletar(anyMap());
    }

    @Test
    void siGoogleFallaResponde502() {
        doThrow(new RestClientException("403")).when(service).autocompletar(anyMap());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.sugerir("Av. Arequipa", TOKEN));

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void detalleDevuelveDireccionYUbicacion() {
        doReturn(Map.of("formattedAddress", "Av. Arequipa 123, Lince 15046",
                "location", Map.of("latitude", -12.085, "longitude", -77.035)))
                .when(service).detalleLugar("ChIJ1", TOKEN);

        LugarDTO l = service.detalle("ChIJ1", TOKEN);

        assertEquals("Av. Arequipa 123, Lince 15046", l.direccion());
        assertEquals(0, new BigDecimal("-12.085").compareTo(l.latitud()));
        assertEquals(0, new BigDecimal("-77.035").compareTo(l.longitud()));
    }

    @Test
    void detalleSinUbicacionEsInvalido() {
        doReturn(Map.of("formattedAddress", "Sin coordenadas")).when(service).detalleLugar(anyString(), anyString());

        assertThrows(IllegalArgumentException.class, () -> service.detalle("ChIJ1", TOKEN));
    }

    @Test
    void detalleRechazaIdentificadoresRaros() {
        assertThrows(IllegalArgumentException.class, () -> service.detalle("../admin", TOKEN));
        verify(service, never()).detalleLugar(anyString(), anyString());
    }
}
