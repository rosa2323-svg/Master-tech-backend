package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.dto.ConcentracionIngresosDTO;
import utp.mastertech.mastertech.dto.IngresoDiarioDTO;
import utp.mastertech.mastertech.dto.IngresoPromedioDTO;
import utp.mastertech.mastertech.dto.ProductosVendidosDTO;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.service.DashboardService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardRestControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardRestController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void tendenciasSinParametrosAgrupaPorDia() throws Exception {
        when(dashboardService.tendenciaVentas(null, null, "dia")).thenReturn(List.of());

        mvc.perform(get("/api/v1/dashboard/tendencias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(dashboardService).tendenciaVentas(null, null, "dia");
    }

    @Test
    void tendenciasPasaAgruparYLasFechasAlServicio() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        when(dashboardService.tendenciaVentas(desde, hasta, "semana")).thenReturn(List.of(
                new TendenciaPuntoDTO(desde, "01 nov", 2, 5, new BigDecimal("300.00"))));

        mvc.perform(get("/api/v1/dashboard/tendencias")
                        .param("agrupar", "semana")
                        .param("desde", "2026-11-01")
                        .param("hasta", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].etiqueta").value("01 nov"))
                .andExpect(jsonPath("$[0].ordenes").value(2))
                .andExpect(jsonPath("$[0].unidades").value(5));
    }

    @Test
    void tendenciasConParametroInvalidoDevuelve400() throws Exception {
        when(dashboardService.tendenciaVentas(null, null, "anio"))
                .thenThrow(new IllegalArgumentException("El parámetro 'agrupar' debe ser dia, semana o mes"));

        mvc.perform(get("/api/v1/dashboard/tendencias").param("agrupar", "anio"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'agrupar' debe ser dia, semana o mes"));
    }

    @Test
    void productosVendidosDevuelveElResumen() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        when(dashboardService.productosVendidos(desde, hasta)).thenReturn(new ProductosVendidosDTO(
                14,
                List.of(new ProductosVendidosDTO.Item(1L, "Laptops", 14, new BigDecimal("5400"))),
                List.of()));

        mvc.perform(get("/api/v1/dashboard/productos-vendidos")
                        .param("desde", "2026-11-01")
                        .param("hasta", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnidades").value(14))
                .andExpect(jsonPath("$.porCategoria[0].nombre").value("Laptops"));
    }

    @Test
    void ingresoDiarioDevuelveHoyAyerYVariacion() throws Exception {
        when(dashboardService.ingresoDiario()).thenReturn(
                new IngresoDiarioDTO(new BigDecimal("150.50"), new BigDecimal("100.00"), new BigDecimal("50.5")));

        mvc.perform(get("/api/v1/dashboard/ingreso-diario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoy").value(150.5))
                .andExpect(jsonPath("$.ayer").value(100.0))
                .andExpect(jsonPath("$.variacionPct").value(50.5));
    }

    @Test
    void ingresoPromedioPasaElRangoYDevuelveLosPromedios() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        when(dashboardService.ingresoPromedio(desde, hasta)).thenReturn(new IngresoPromedioDTO(
                new BigDecimal("300"), 3, 10, new BigDecimal("30.00"), new BigDecimal("100.00")));

        mvc.perform(get("/api/v1/dashboard/ingreso-promedio")
                        .param("desde", "2026-11-01")
                        .param("hasta", "2026-11-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordenes").value(3))
                .andExpect(jsonPath("$.dias").value(10))
                .andExpect(jsonPath("$.ticketPromedio").value(100.0));
    }

    @Test
    void ingresoPromedioConRangoInvalidoDevuelve400() throws Exception {
        LocalDate desde = LocalDate.of(2026, 11, 10);
        LocalDate hasta = LocalDate.of(2026, 11, 1);
        when(dashboardService.ingresoPromedio(desde, hasta))
                .thenThrow(new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'"));

        mvc.perform(get("/api/v1/dashboard/ingreso-promedio")
                        .param("desde", "2026-11-10")
                        .param("hasta", "2026-11-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concentracionIngresosDevuelveElResumen() throws Exception {
        when(dashboardService.concentracionIngresos(null, null)).thenReturn(new ConcentracionIngresosDTO(
                new BigDecimal("1000"), new BigDecimal("70.0"),
                List.of(new ConcentracionIngresosDTO.Item(1L, "Laptop X", new BigDecimal("700"), new BigDecimal("70.0"))),
                new BigDecimal("300"), new BigDecimal("30.0")));

        mvc.perform(get("/api/v1/dashboard/concentracion-ingresos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.top5Pct").value(70.0))
                .andExpect(jsonPath("$.productos[0].nombre").value("Laptop X"))
                .andExpect(jsonPath("$.otrosPct").value(30.0));
    }
}
