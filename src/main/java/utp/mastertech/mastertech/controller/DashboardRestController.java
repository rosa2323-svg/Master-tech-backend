package utp.mastertech.mastertech.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.service.DashboardService;

import java.time.LocalDate;
import java.util.List;

/** Datos del dashboard de KPI (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardRestController {

    @Autowired
    private DashboardService dashboardService;

    // GET /api/v1/dashboard/tendencias?agrupar=dia&desde=2026-10-14&hasta=2026-11-13
    @GetMapping("/tendencias")
    public List<TendenciaPuntoDTO> tendencias(
            @RequestParam(defaultValue = "dia") String agrupar,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardService.tendenciaVentas(desde, hasta, agrupar);
    }
}