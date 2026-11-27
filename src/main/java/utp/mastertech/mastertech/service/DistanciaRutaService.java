package utp.mastertech.mastertech.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import utp.mastertech.mastertech.model.PuntoReparto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Distancia por ruta (en auto) entre una sede y el cliente, con la Routes API de Google.
 *
 * Si no hay clave configurada o Google falla, devuelve vacío y el llamador usa la distancia
 * en línea recta: cotizar nunca debe romperse por Google. Los resultados se guardan en memoria
 * (ubicaciones redondeadas a ~11 m) para gastar menos cuota.
 */
@Service
public class DistanciaRutaService {

    private static final Logger log = LoggerFactory.getLogger(DistanciaRutaService.class);
    private static final String URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final Pattern DISTANCIA = Pattern.compile("\"distanceMeters\"\\s*:\\s*(\\d+)");
    private static final int MAX_CACHE = 500;

    /** Clave de SERVIDOR (variable GOOGLE_MAPS_SERVER_KEY). Vacía = sin Google. */
    @Value("${google.maps.server-key:}")
    private String apiKey;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private final Map<String, BigDecimal> cache = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, BigDecimal> eldest) {
                    return size() > MAX_CACHE;
                }
            });

    public boolean habilitado() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** @return kilómetros por ruta con 2 decimales, o vacío si no se pudo obtener. */
    public Optional<BigDecimal> distanciaPorRutaKm(PuntoReparto sede, BigDecimal latitud, BigDecimal longitud) {
        if (!habilitado()) {
            return Optional.empty();
        }
        String clave = sede.getId() + "|" + latitud.setScale(4, RoundingMode.HALF_UP).toPlainString()
                + "|" + longitud.setScale(4, RoundingMode.HALF_UP).toPlainString();
        BigDecimal guardado = cache.get(clave);
        if (guardado != null) {
            return Optional.of(guardado);
        }
        try {
            Optional<BigDecimal> km = extraerKm(solicitar(cuerpo(sede, latitud, longitud)));
            km.ifPresent(v -> cache.put(clave, v));
            return km;
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("No se pudo obtener la ruta con Google ({}); se usa la distancia en línea recta.", e.getMessage());
            return Optional.empty();
        }
    }

    /** Llamada HTTP (aislada para poder simularla en pruebas). */
    String solicitar(String cuerpo) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(4))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", "routes.distanceMeters")
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                .build();
        HttpResponse<String> respuesta = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (respuesta.statusCode() != 200) {
            throw new IllegalStateException("Google respondió HTTP " + respuesta.statusCode());
        }
        return respuesta.body();
    }

    static String cuerpo(PuntoReparto sede, BigDecimal latitud, BigDecimal longitud) {
        return String.format(Locale.ROOT,
                "{\"origin\":{\"location\":{\"latLng\":{\"latitude\":%s,\"longitude\":%s}}},"
                        + "\"destination\":{\"location\":{\"latLng\":{\"latitude\":%s,\"longitude\":%s}}},"
                        + "\"travelMode\":\"DRIVE\"}",
                sede.getLatitud().toPlainString(), sede.getLongitud().toPlainString(),
                latitud.toPlainString(), longitud.toPlainString());
    }

    /** Google omite distanceMeters cuando es 0: una ruta sin el campo cuenta como 0 km. */
    static Optional<BigDecimal> extraerKm(String json) {
        if (json == null || !json.contains("\"routes\"")) {
            return Optional.empty();
        }
        Matcher m = DISTANCIA.matcher(json);
        long metros = m.find() ? Long.parseLong(m.group(1)) : 0L;
        return Optional.of(BigDecimal.valueOf(metros).divide(BigDecimal.valueOf(1000), 2, RoundingMode.HALF_UP));
    }
}
