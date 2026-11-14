package utp.mastertech.mastertech.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.IngresoDiarioDTO;
import utp.mastertech.mastertech.dto.ProductosVendidosDTO;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.service.DashboardService;
import utp.mastertech.mastertech.dto.ConcentracionIngresosDTO;
import utp.mastertech.mastertech.dto.IngresoPromedioDTO;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardRestController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/tendencias")
    public List<TendenciaPuntoDTO> tendencias(
            @RequestParam(defaultValue = "dia") String agrupar,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.tendenciaVentas(desde, hasta, agrupar);
    }

    @GetMapping("/productos-vendidos")
    public ProductosVendidosDTO productosVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.productosVendidos(desde, hasta);
    }

    @GetMapping("/ingreso-diario")
    public IngresoDiarioDTO ingresoDiario() {
        return dashboardService.ingresoDiario();
    }

    @GetMapping("/ingreso-promedio")
    public IngresoPromedioDTO ingresoPromedio(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.ingresoPromedio(desde, hasta);
    }

    @GetMapping("/concentracion-ingresos")
    public ConcentracionIngresosDTO concentracionIngresos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.concentracionIngresos(desde, hasta);
    }
}