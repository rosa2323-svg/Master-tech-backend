package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.ActivoRequest;
import utp.mastertech.mastertech.dto.ConfigEnvioDTO;
import utp.mastertech.mastertech.dto.DiaNoLaborableDTO;
import utp.mastertech.mastertech.dto.HuecoTarifaDTO;
import utp.mastertech.mastertech.dto.PuntoRepartoDTO;
import utp.mastertech.mastertech.dto.TarifaEnvioDTO;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.service.EnvioAdminService;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.stream.Collectors;

/** Configuración de envíos (sedes, tarifas, hora de corte y feriados). Solo ADMIN (ver SecurityConfig: /api/v1/**). */
@RestController
@RequestMapping("/api/v1/envios")
public class EnvioRestController {

    @Autowired
    private EnvioAdminService envioService;

    @Autowired
    private EnvioMapper envioMapper;

    // ---------------------------------------------------------------- puntos de reparto

    @GetMapping("/puntos-reparto")
    public List<PuntoRepartoDTO> listarPuntos() {
        return envioService.listarPuntos().stream()
                .map(p -> envioMapper.toDTO(p))
                .collect(Collectors.toList());
    }

    @PostMapping("/puntos-reparto")
    public ResponseEntity<PuntoRepartoDTO> crearPunto(@Valid @RequestBody PuntoRepartoDTO dto) {
        dto.setId(null);
        PuntoReparto punto = envioMapper.toEntity(dto);
        envioService.guardarPunto(punto);
        return ResponseEntity.status(HttpStatus.CREATED).body(envioMapper.toDTO(punto));
    }

    @PutMapping("/puntos-reparto/{id}")
    public PuntoRepartoDTO actualizarPunto(@PathVariable Long id, @Valid @RequestBody PuntoRepartoDTO dto) {
        PuntoReparto punto = envioService.obtenerPunto(id);
        envioMapper.updateEntityFromDTO(dto, punto);
        envioService.guardarPunto(punto);
        return envioMapper.toDTO(punto);
    }

    /** Activa o desactiva el almacén (no se elimina: los pedidos lo referencian). */
    @PatchMapping("/puntos-reparto/{id}/activo")
    public PuntoRepartoDTO cambiarActivoPunto(@PathVariable Long id, @Valid @RequestBody ActivoRequest body) {
        return envioMapper.toDTO(envioService.cambiarActivoPunto(id, body.getActivo()));
    }

    // ---------------------------------------------------------------- tarifas

    @GetMapping("/tarifas")
    public List<TarifaEnvioDTO> listarTarifas() {
        return envioService.listarTarifas().stream()
                .map(t -> envioMapper.toDTO(t))
                .collect(Collectors.toList());
    }

    /** Rangos de km sin tarifa activa, para avisar al admin (esas distancias darían "sin cobertura"). */
    @GetMapping("/tarifas/huecos")
    public List<HuecoTarifaDTO> huecosDeTarifas() {
        return envioService.calcularHuecos();
    }

    @PostMapping("/tarifas")
    public ResponseEntity<TarifaEnvioDTO> crearTarifa(@Valid @RequestBody TarifaEnvioDTO dto) {
        dto.setId(null);
        TarifaEnvio tarifa = envioMapper.toEntity(dto);
        envioService.guardarTarifa(tarifa);
        return ResponseEntity.status(HttpStatus.CREATED).body(envioMapper.toDTO(tarifa));
    }

    @PutMapping("/tarifas/{id}")
    public TarifaEnvioDTO actualizarTarifa(@PathVariable Long id, @Valid @RequestBody TarifaEnvioDTO dto) {
        TarifaEnvio tarifa = envioService.obtenerTarifa(id);
        envioMapper.updateEntityFromDTO(dto, tarifa);
        envioService.guardarTarifa(tarifa);
        return envioMapper.toDTO(tarifa);
    }

    @PatchMapping("/tarifas/{id}/activo")
    public TarifaEnvioDTO cambiarActivoTarifa(@PathVariable Long id, @Valid @RequestBody ActivoRequest body) {
        return envioMapper.toDTO(envioService.cambiarActivoTarifa(id, body.getActivo()));
    }

    // ---------------------------------------------------------------- configuración

    @GetMapping("/configuracion")
    public ConfigEnvioDTO obtenerConfiguracion() {
        return envioMapper.toDTO(envioService.obtenerConfig());
    }

    @PutMapping("/configuracion")
    public ConfigEnvioDTO guardarConfiguracion(@Valid @RequestBody ConfigEnvioDTO dto) {
        ConfigEnvio config = envioService.obtenerConfig();
        envioMapper.updateEntityFromDTO(dto, config);
        envioService.guardarConfig(config);
        return envioMapper.toDTO(config);
    }

    // ---------------------------------------------------------------- feriados

    @GetMapping("/feriados")
    public List<DiaNoLaborableDTO> listarFeriados() {
        return envioService.listarFeriados().stream()
                .map(f -> envioMapper.toDTO(f))
                .collect(Collectors.toList());
    }

    @PostMapping("/feriados")
    public ResponseEntity<DiaNoLaborableDTO> crearFeriado(@Valid @RequestBody DiaNoLaborableDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(envioMapper.toDTO(envioService.agregarFeriado(dto.getFecha(), dto.getDescripcion())));
    }

    /** La fecha va en la URL con formato AAAA-MM-DD. */
    @DeleteMapping("/feriados/{fecha}")
    public ResponseEntity<Void> eliminarFeriado(@PathVariable String fecha) {
        LocalDate dia;
        try {
            dia = LocalDate.parse(fecha);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha no válida: use el formato AAAA-MM-DD.");
        }
        envioService.eliminarFeriado(dia);
        return ResponseEntity.noContent().build();
    }
}
