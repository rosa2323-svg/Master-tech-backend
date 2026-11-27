package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.ResolverReembolsoRequest;
import utp.mastertech.mastertech.dto.SolicitudReembolsoDTO;
import utp.mastertech.mastertech.mapper.ReembolsoMapper;
import utp.mastertech.mastertech.service.ReembolsoService;

import java.util.List;
import java.util.stream.Collectors;

/** Solicitudes de reembolso: solo ADMIN (ver SecurityConfig, /api/v1/**). */
@RestController
@RequestMapping("/api/v1/reembolsos")
public class ReembolsoRestController {

    @Autowired
    private ReembolsoService reembolsoService;

    @Autowired
    private ReembolsoMapper reembolsoMapper;

    /** Pendientes primero. Con ?estado=PENDIENTE|APROBADA|RECHAZADA se filtra. */
    @GetMapping
    public List<SolicitudReembolsoDTO> listar(@RequestParam(required = false) String estado) {
        return reembolsoService.listar(estado).stream().map(reembolsoMapper::toDTO).collect(Collectors.toList());
    }

    @PostMapping("/{id}/aprobar")
    public SolicitudReembolsoDTO aprobar(@PathVariable Long id, @Valid @RequestBody(required = false) ResolverReembolsoRequest body,
                                         Authentication auth) {
        return reembolsoMapper.toDTO(reembolsoService.aprobar(id, auth == null ? null : auth.getName(),
                body == null ? null : body.getNota()));
    }

    @PostMapping("/{id}/rechazar")
    public SolicitudReembolsoDTO rechazar(@PathVariable Long id, @Valid @RequestBody ResolverReembolsoRequest body,
                                          Authentication auth) {
        return reembolsoMapper.toDTO(reembolsoService.rechazar(id, auth == null ? null : auth.getName(), body.getNota()));
    }
}
