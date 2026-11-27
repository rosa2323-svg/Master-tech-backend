package utp.mastertech.mastertech.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class ErrorJsonTest {

    @Test
    void escribeElMismoFormatoQueErrorResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ErrorJson.escribir(request, response, HttpStatus.FORBIDDEN, "No tienes permisos para esta acción");

        String json = response.getContentAsString();
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertTrue(json.contains("\"status\":403"));
        assertTrue(json.contains("\"error\":\"Forbidden\""));
        assertTrue(json.contains("\"message\":\"No tienes permisos para esta acción\""));
        assertTrue(json.contains("\"path\":\"/api/v1/me\""));
        assertTrue(json.contains("\"timestamp\":"));
    }

    @Test
    void escapaComillasBarrasYSaltosDeLinea() {
        assertEquals("a\\\"b\\\\c\\nd", ErrorJson.escapar("a\"b\\c\nd"));
    }

    @Test
    void textoNuloSeEscribeVacio() {
        assertEquals("", ErrorJson.escapar(null));
    }

    @Test
    void caracteresDeControlSeEscapanComoUnicode() {
        assertEquals("\\u0001", ErrorJson.escapar("\u0001"));
    }
}
