package utp.mastertech.mastertech.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Escribe un error con el mismo formato que {@link ErrorResponse} para las respuestas que se generan
 * FUERA de los controladores (filtro JWT y SecurityConfig), donde {@link ApiExceptionHandler} no llega.
 * Arma el JSON a mano para no depender de un ObjectMapper dentro de los filtros.
 */
public final class ErrorJson {

    private ErrorJson() {
    }

    public static void escribir(HttpServletRequest request, HttpServletResponse response,
                                HttpStatus status, String mensaje) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(construir(status, mensaje, request.getRequestURI()));
    }

    static String construir(HttpStatus status, String mensaje, String path) {
        return "{\"timestamp\":\"" + LocalDateTime.now() + "\""
                + ",\"status\":" + status.value()
                + ",\"error\":\"" + escapar(status.getReasonPhrase()) + "\""
                + ",\"message\":\"" + escapar(mensaje) + "\""
                + ",\"path\":\"" + escapar(path) + "\"}";
    }

    static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(texto.length() + 8);
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
