package utp.mastertech.mastertech.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import utp.mastertech.mastertech.dto.LugarDTO;
import utp.mastertech.mastertech.dto.SugerenciaDTO;
import utp.mastertech.mastertech.exception.ConflictException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Autocompletado de direcciones con Places API (New) y dirección de un punto marcado en el mapa (Geocoding API). Pasa por el back para que la clave de Google
 * viva solo en el servidor (GOOGLE_MAPS_SERVER_KEY) y no en el navegador.
 * Las sugerencias y el detalle comparten el mismo sessionToken para que Google los cobre como una sesión.
 */
@Service
public class DireccionService {

    private static final Logger log = LoggerFactory.getLogger(DireccionService.class);
    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Pattern PLACE_ID = Pattern.compile("[A-Za-z0-9_-]{1,300}");
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    @Value("${google.maps.server-key:}")
    private String apiKey;

    private final RestClient rest = crearCliente();

    private static RestClient crearCliente() {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().baseUrl("https://places.googleapis.com/v1").requestFactory(fabrica).build();
    }

    public boolean habilitado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public List<SugerenciaDTO> sugerir(String texto, String token) {
        exigirHabilitado();
        String consulta = texto == null ? "" : texto.trim();
        if (consulta.length() < 3 || consulta.length() > 150) {
            throw new IllegalArgumentException("Escribe entre 3 y 150 caracteres para buscar una dirección.");
        }
        validarToken(token);
        Map<String, Object> cuerpo = Map.of(
                "input", consulta,
                "sessionToken", token,
                "languageCode", "es",
                "includedRegionCodes", List.of("pe"),
                "locationBias", Map.of("circle", Map.of(
                        "center", Map.of("latitude", -12.0464, "longitude", -77.0428),
                        "radius", 50000.0)));
        try {
            return aSugerencias(autocompletar(cuerpo));
        } catch (RuntimeException e) {
            throw falloGoogle(e);
        }
    }

    public LugarDTO detalle(String placeId, String token) {
        exigirHabilitado();
        if (placeId == null || !PLACE_ID.matcher(placeId).matches()) {
            throw new IllegalArgumentException("Identificador de lugar inválido.");
        }
        validarToken(token);
        try {
            return aLugar(detalleLugar(placeId, token));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw falloGoogle(e);
        }
    }

    /**
     * Dirección de un punto marcado con el pin del mapa. La ubicación que se guarda es la del pin (exacta);
     * Google solo aporta el texto de la dirección.
     */
    public LugarDTO inversa(BigDecimal latitud, BigDecimal longitud) {
        exigirHabilitado();
        if (latitud == null || longitud == null
                || latitud.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitud.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("La ubicación marcada no es válida.");
        }
        BigDecimal lat = latitud.setScale(6, RoundingMode.HALF_UP);
        BigDecimal lng = longitud.setScale(6, RoundingMode.HALF_UP);
        try {
            return new LugarDTO(aDireccion(geocodificar(lat, lng)), lat, lng);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw falloGoogle(e);
        }
    }

    // ---- llamadas HTTP (aisladas para poder simularlas en pruebas) ----

    Map<String, Object> autocompletar(Map<String, Object> cuerpo) {
        return rest.post().uri("/places:autocomplete")
                .header("X-Goog-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(MAPA);
    }

    Map<String, Object> detalleLugar(String placeId, String token) {
        return rest.get().uri("/places/{id}?languageCode=es&sessionToken={t}", placeId, token)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "formattedAddress,location")
                .retrieve()
                .body(MAPA);
    }

    Map<String, Object> geocodificar(BigDecimal lat, BigDecimal lng) {
        return rest.get()
                .uri("https://maps.googleapis.com/maps/api/geocode/json?latlng={lat},{lng}&language=es&region=pe&key={key}",
                        lat.toPlainString(), lng.toPlainString(), apiKey)
                .retrieve()
                .body(MAPA);
    }

    // ---- conversión de respuestas ----

    /** Primera dirección legible (se evitan los «plus codes» tipo 9XQ4+8V cuando hay algo mejor). */
    @SuppressWarnings("unchecked")
    static String aDireccion(Map<String, Object> respuesta) {
        Object estado = respuesta == null ? null : respuesta.get("status");
        if ("ZERO_RESULTS".equals(estado)) {
            throw new IllegalArgumentException("No encontramos una dirección en ese punto. Prueba con otro punto del mapa.");
        }
        if (!"OK".equals(estado) || !(respuesta.get("results") instanceof List<?> resultados)) {
            throw new IllegalStateException("Respuesta de geocodificación: " + estado);
        }
        String respaldo = null;
        for (Object r : resultados) {
            if (!(r instanceof Map<?, ?> resultado) || !(resultado.get("formatted_address") instanceof String texto)) continue;
            boolean plusCode = resultado.get("types") instanceof List<?> tipos && tipos.contains("plus_code");
            if (!plusCode) return texto;
            if (respaldo == null) respaldo = texto;
        }
        if (respaldo != null) return respaldo;
        throw new IllegalArgumentException("No encontramos una dirección en ese punto. Prueba con otro punto del mapa.");
    }

    @SuppressWarnings("unchecked")
    static List<SugerenciaDTO> aSugerencias(Map<String, Object> respuesta) {
        List<SugerenciaDTO> resultado = new ArrayList<>();
        if (respuesta == null || !(respuesta.get("suggestions") instanceof List<?> lista)) {
            return resultado;
        }
        for (Object item : lista) {
            if (!(item instanceof Map<?, ?> s) || !(s.get("placePrediction") instanceof Map<?, ?> p)) continue;
            Object id = p.get("placeId");
            Object texto = p.get("text") instanceof Map<?, ?> t ? t.get("text") : null;
            if (id instanceof String placeId && texto instanceof String descripcion) {
                resultado.add(new SugerenciaDTO(placeId, descripcion));
            }
        }
        return resultado;
    }

    static LugarDTO aLugar(Map<String, Object> respuesta) {
        if (respuesta == null || !(respuesta.get("location") instanceof Map<?, ?> ubicacion)
                || !(ubicacion.get("latitude") instanceof Number lat)
                || !(ubicacion.get("longitude") instanceof Number lng)) {
            throw new IllegalArgumentException("Esa dirección no tiene una ubicación válida.");
        }
        Object direccion = respuesta.get("formattedAddress");
        return new LugarDTO(direccion instanceof String d ? d : "",
                new BigDecimal(lat.toString()).setScale(6, RoundingMode.HALF_UP),
                new BigDecimal(lng.toString()).setScale(6, RoundingMode.HALF_UP));
    }

    // ---- validaciones y errores ----

    private void exigirHabilitado() {
        if (!habilitado()) {
            throw new ConflictException("El autocompletado de direcciones no está disponible.");
        }
    }

    private static void validarToken(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("Token de sesión inválido.");
        }
    }

    private static ResponseStatusException falloGoogle(RuntimeException e) {
        // No se registra e.getMessage(): en errores de conexión incluye la URL completa, con la clave de Google.
        log.warn("Falló la consulta de direcciones a Google ({})", e.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el servicio de direcciones.");
    }
}
