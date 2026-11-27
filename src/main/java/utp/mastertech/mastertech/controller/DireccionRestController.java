package utp.mastertech.mastertech.controller;

import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import utp.mastertech.mastertech.dto.LugarDTO;
import utp.mastertech.mastertech.dto.SugerenciaDTO;
import utp.mastertech.mastertech.service.DireccionService;

import java.util.List;
import java.util.Map;

/** Autocompletado de direcciones para el carrito (cualquier usuario autenticado). */
@RestController
@RequestMapping("/api/v1/pedidos/direcciones")
public class DireccionRestController {

    @Autowired
    private DireccionService direccionService;

    /** El front usa esto para decidir entre autocompletado y selector de distrito. */
    @GetMapping("/disponible")
    public Map<String, Boolean> disponible() {
        return Map.of("disponible", direccionService.habilitado());
    }

    @GetMapping("/sugerencias")
    public List<SugerenciaDTO> sugerencias(@RequestParam String texto, @RequestParam String token) {
        return direccionService.sugerir(texto, token);
    }

    /** Dirección de un punto marcado con el pin del mapa. */
    @GetMapping("/inversa")
    public LugarDTO inversa(@RequestParam BigDecimal latitud, @RequestParam BigDecimal longitud) {
        return direccionService.inversa(latitud, longitud);
    }

    @GetMapping("/detalle")
    public LugarDTO detalle(@RequestParam String placeId, @RequestParam String token) {
        return direccionService.detalle(placeId, token);
    }
}
