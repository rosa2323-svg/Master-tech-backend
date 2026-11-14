package utp.mastertech.mastertech.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.CambioEstadoDTO;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.service.VentaService;

import java.util.List;
import java.util.stream.Collectors;

/** Vista del despachador (DESPACHADOR y ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/despacho")
public class DespachoController {

    @Autowired
    private VentaService ventaService;

    @Autowired
    private VentaMapper ventaMapper;

    @GetMapping("/pedidos")
    public List<VentaDTO> listar() {
        return ventaService.listarParaDespacho().stream()
                .map(ventaMapper::toDTO)
                .collect(Collectors.toList());
    }

    @PatchMapping("/pedidos/{id}/estado")
    public VentaDTO cambiarEstado(@PathVariable Long id, @RequestBody CambioEstadoDTO body) {
        return ventaMapper.toDTO(ventaService.cambiarEstado(id, body.getEstado()));
    }
}
