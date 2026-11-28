package utp.mastertech.mastertech.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Comprobación pública de que el servicio responde. No toca la base de datos ni expone configuración. */
@RestController
public class HealthController {

    @GetMapping({"/", "/api/health"})
    public Map<String, Object> estado() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("servicio", "mastertech");
        cuerpo.put("estado", "UP");
        cuerpo.put("hora", Instant.now().toString());
        return cuerpo;
    }
}
