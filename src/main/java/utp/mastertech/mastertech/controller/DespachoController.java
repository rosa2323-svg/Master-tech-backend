package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.CambioEstadoDTO;
import utp.mastertech.mastertech.dto.EnvioEventoDTO;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.service.VentaService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Vista del despachador (DESPACHADOR y ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/despacho")
public class DespachoController {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaMapper ventaMapper;

    @Autowired
    private EnvioMapper envioMapper;

    @GetMapping("/pedidos")
    public List<VentaDTO> listar() {
        Set<Long> conReembolso = ventaService.ventasConReembolsoPendiente();
        return ventaService.listarParaDespacho().stream()
                .map(ventaMapper::toDTO)
                .peek(dto -> dto.setReembolsoPendiente(conReembolso.contains(dto.getId())))
                .collect(Collectors.toList());
    }

    /** Avanza el pedido al siguiente estado de su flujo y deja constancia de quién lo hizo. */
    @PatchMapping("/pedidos/{id}/estado")
    public VentaDTO cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoDTO body,
                                  Authentication auth) {
        return ventaMapper.toDTO(ventaService.cambiarEstado(id, body.getEstado(), body.getNota(),
                auth == null ? null : auth.getName()));
    }

    @GetMapping("/pedidos/{id}/eventos")
    public List<EnvioEventoDTO> eventos(@PathVariable Long id) {
        return ventaService.listarEventos(id).stream().map(envioMapper::toDTO).collect(Collectors.toList());
    }
}
