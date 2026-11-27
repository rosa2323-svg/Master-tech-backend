package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.CambiarPasswordRequest;
import utp.mastertech.mastertech.dto.EnvioEventoDTO;
import utp.mastertech.mastertech.dto.SolicitudReembolsoDTO;
import utp.mastertech.mastertech.dto.SolicitudReembolsoRequest;
import utp.mastertech.mastertech.dto.PerfilDTO;
import utp.mastertech.mastertech.dto.PerfilUpdateRequest;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.mapper.ReembolsoMapper;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.service.ReembolsoService;
import utp.mastertech.mastertech.service.UsuarioService;
import utp.mastertech.mastertech.service.VentaService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Datos del cliente autenticado (tabla usuarios): perfil, contraseña e historial de compras. */
@RestController
@RequestMapping("/api/v1/me")
public class MeRestController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private VentaMapper ventaMapper;

    @Autowired
    private EnvioMapper envioMapper;

    @Autowired
    private VentaService ventaService;

    @Autowired
    private ReembolsoService reembolsoService;

    @Autowired
    private ReembolsoMapper reembolsoMapper;

    @GetMapping
    public PerfilDTO verPerfil(Authentication auth) {
        return armarPerfil(auth.getName());
    }

    @PutMapping
    public PerfilDTO actualizarPerfil(@Valid @RequestBody PerfilUpdateRequest req, Authentication auth) {
        usuarioService.actualizarDatosPersonales(auth.getName(), req.getNombre().trim(),
                req.getDireccion(), req.getNumeroTelefono());
        return armarPerfil(auth.getName());
    }

    @PutMapping("/password")
    public ResponseEntity<Map<String, String>> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest req,
                                                               Authentication auth) {
        String error = usuarioService.cambiarPassword(auth.getName(),
                req.getPasswordActual(), req.getPasswordNuevo(), req.getPasswordConfirm());
        if (error != null) {
            return ResponseEntity.badRequest().body(Map.of("error", error));
        }
        return ResponseEntity.ok(Map.of("mensaje", "Contraseña actualizada"));
    }

    @GetMapping("/pedidos")
    public List<VentaDTO> historial(Authentication auth) {
        Usuario usuario = usuarioService.obtenerPorEmail(auth.getName());
        return usuarioService.obtenerHistorialCompras(usuario).stream()
                .map(ventaMapper::toDTO)
                .collect(Collectors.toList());
    }

    /** Seguimiento de un pedido propio: por qué estados pasó y cuándo. */
    @GetMapping("/pedidos/{id}/seguimiento")
    public List<EnvioEventoDTO> seguimiento(@PathVariable Long id, Authentication auth) {
        return ventaService.listarEventosDelCliente(id, auth.getName()).stream()
                .map(envioMapper::toDTO)
                .collect(Collectors.toList());
    }

    /** El cliente pide el reembolso de un pedido propio (solo antes de que salga a reparto). */
    @PostMapping("/pedidos/{id}/reembolso")
    @ResponseStatus(HttpStatus.CREATED)
    public SolicitudReembolsoDTO solicitarReembolso(@PathVariable Long id,
                                                    @Valid @RequestBody SolicitudReembolsoRequest req,
                                                    Authentication auth) {
        return reembolsoMapper.toDTO(reembolsoService.solicitar(id, auth.getName(), req.getMotivo()));
    }

    /** Solicitudes de reembolso del cliente (para ver en Mis compras si están pendientes o rechazadas). */
    @GetMapping("/reembolsos")
    public List<SolicitudReembolsoDTO> misReembolsos(Authentication auth) {
        return reembolsoService.listarDelCliente(auth.getName()).stream()
                .map(reembolsoMapper::toDTO)
                .collect(Collectors.toList());
    }

    private PerfilDTO armarPerfil(String email) {
        Usuario u = usuarioService.obtenerPorEmail(email);
        PerfilDTO dto = new PerfilDTO();
        dto.setId(u.getId());
        dto.setNombre(u.getNombre());
        dto.setEmail(u.getEmail());
        dto.setRol(u.getRol());
        dto.setDni(u.getDni());
        dto.setDireccion(u.getDireccion());
        dto.setNumeroTelefono(u.getNumeroTelefono());
        dto.setFechaRegistro(u.getFechaRegistro());
        return dto;
    }
}
