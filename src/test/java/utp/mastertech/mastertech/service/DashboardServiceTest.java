package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.mastertech.mastertech.dto.ConcentracionIngresosDTO;
import utp.mastertech.mastertech.dto.IngresoDiarioDTO;
import utp.mastertech.mastertech.dto.IngresoPromedioDTO;
import utp.mastertech.mastertech.dto.ProductosVendidosDTO;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.TendenciaVentaProjection;
import utp.mastertech.mastertech.repository.VentaAgrupadaProjection;
import utp.mastertech.mastertech.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private VentaRepository ventaRepository;
    @Mock
    private DetalleVentaRepository detalleVentaRepository;

    @InjectMocks
    private DashboardService service;

    // ---------- helpers ----------

    private TendenciaVentaProjection tendencia(LocalDate periodo, long ordenes, long unidades, String ingreso) {
        TendenciaVentaProjection p = mock(TendenciaVentaProjection.class);
        when(p.getPeriodo()).thenReturn(periodo);
        when(p.getOrdenes()).thenReturn(ordenes);
        when(p.getUnidades()).thenReturn(unidades);
        when(p.getIngreso()).thenReturn(new BigDecimal(ingreso));
        return p;
    }

    /** lenient: cada prueba usa solo algunos de los getters (p. ej. concentración no usa unidades). */
    private VentaAgrupadaProjection agrupada(long id, String nombre, long unidades, String ingreso) {
        VentaAgrupadaProjection p = mock(VentaAgrupadaProjection.class);
        lenient().when(p.getId()).thenReturn(id);
        lenient().when(p.getNombre()).thenReturn(nombre);
        lenient().when(p.getUnidades()).thenReturn(unidades);
        lenient().when(p.getIngreso()).thenReturn(new BigDecimal(ingreso));
        return p;
    }

    private static void assertDecimal(String esperado, BigDecimal real) {
        assertNotNull(real);
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                "esperado " + esperado + " pero fue " + real);
    }

    // ---------- tendenciaVentas ----------

    @Test
    void tendenciaPorDiaRellenaConCerosLosDiasSinVentas() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 3);
        when(ventaRepository.tendenciaVentas("day", desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(tendencia(LocalDate.of(2026, 11, 2), 2, 5, "300.00")));

        List<TendenciaPuntoDTO> serie = service.tendenciaVentas(desde, hasta, "dia");

        assertEquals(3, serie.size());
        assertEquals(LocalDate.of(2026, 11, 1), serie.get(0).getPeriodo());
        assertEquals(0, serie.get(0).getOrdenes());
        assertDecimal("0", serie.get(0).getIngreso());
        assertEquals(2, serie.get(1).getOrdenes());
        assertEquals(5, serie.get(1).getUnidades());
        assertDecimal("300.00", serie.get(1).getIngreso());
        assertEquals(LocalDate.of(2026, 11, 3), serie.get(2).getPeriodo());
        assertEquals(0, serie.get(2).getUnidades());
    }

    @Test
    void tendenciaPorSemanaArrancaEnElLunesDeLaSemanaDeDesde() {
        // 4-nov-2026 es miércoles → la primera semana empieza el lunes 2-nov
        LocalDate desde = LocalDate.of(2026, 11, 4);
        LocalDate hasta = LocalDate.of(2026, 11, 17);
        when(ventaRepository.tendenciaVentas("week", desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(tendencia(LocalDate.of(2026, 11, 9), 4, 8, "1000")));

        List<TendenciaPuntoDTO> serie = service.tendenciaVentas(desde, hasta, "semana");

        assertEquals(3, serie.size());
        assertEquals(LocalDate.of(2026, 11, 2), serie.get(0).getPeriodo());
        assertEquals(LocalDate.of(2026, 11, 9), serie.get(1).getPeriodo());
        assertEquals(LocalDate.of(2026, 11, 16), serie.get(2).getPeriodo());
        assertEquals(4, serie.get(1).getOrdenes());
        assertEquals(0, serie.get(0).getOrdenes());
    }

    @Test
    void tendenciaPorMesUsaElPrimerDiaDeCadaMes() {
        LocalDate desde = LocalDate.of(2026, 9, 15);
        LocalDate hasta = LocalDate.of(2026, 11, 14);
        when(ventaRepository.tendenciaVentas("month", desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        List<TendenciaPuntoDTO> serie = service.tendenciaVentas(desde, hasta, "mes");

        assertEquals(3, serie.size());
        assertEquals(LocalDate.of(2026, 9, 1), serie.get(0).getPeriodo());
        assertEquals(LocalDate.of(2026, 10, 1), serie.get(1).getPeriodo());
        assertEquals(LocalDate.of(2026, 11, 1), serie.get(2).getPeriodo());
        assertTrue(serie.get(0).getEtiqueta().contains("2026"));
    }

    @Test
    void tendenciaAceptaElModoEnMayusculasYNulo() {
        LocalDate dia = LocalDate.of(2026, 11, 1);
        when(ventaRepository.tendenciaVentas(eq("month"), any(), any())).thenReturn(List.of());
        when(ventaRepository.tendenciaVentas(eq("day"), any(), any())).thenReturn(List.of());

        assertEquals(1, service.tendenciaVentas(dia, dia, "MES").size());
        assertEquals(1, service.tendenciaVentas(dia, dia, null).size());
    }

    @Test
    void tendenciaSinFechasUsaLosUltimos30Dias() {
        when(ventaRepository.tendenciaVentas(eq("day"), any(), any())).thenReturn(List.of());

        List<TendenciaPuntoDTO> serie = service.tendenciaVentas(null, null, "dia");

        assertEquals(30, serie.size());
        assertEquals(LocalDate.now(), serie.get(29).getPeriodo());
    }

    @Test
    void tendenciaConModoInvalidoLanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.tendenciaVentas(null, null, "anio"));

        assertTrue(ex.getMessage().contains("dia, semana o mes"));
        verifyNoInteractions(ventaRepository);
    }

    @Test
    void tendenciaConDesdePosteriorAHastaLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.tendenciaVentas(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1), "dia"));
        verifyNoInteractions(ventaRepository);
    }

    // ---------- productosVendidos ----------

    @Test
    void productosVendidosSumaLasUnidadesPorCategoriaYTrae5Productos() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        var d = desde.atStartOfDay();
        var h = hasta.plusDays(1).atStartOfDay();
        when(detalleVentaRepository.unidadesPorCategoria(d, h)).thenReturn(List.of(
                agrupada(1, "Laptops", 10, "5000"),
                agrupada(2, "Audio", 4, "400")));
        when(detalleVentaRepository.topProductos(d, h, 5)).thenReturn(List.of(
                agrupada(7, "Laptop X", 6, "3000")));

        ProductosVendidosDTO r = service.productosVendidos(desde, hasta);

        assertEquals(14, r.getTotalUnidades());
        assertEquals(2, r.getPorCategoria().size());
        assertEquals("Laptops", r.getPorCategoria().get(0).getNombre());
        assertEquals(10, r.getPorCategoria().get(0).getUnidades());
        assertEquals(1, r.getTopProductos().size());
        assertEquals(7L, r.getTopProductos().get(0).getId());
        assertDecimal("3000", r.getTopProductos().get(0).getIngreso());
    }

    @Test
    void productosVendidosSinVentasDevuelveCeros() {
        when(detalleVentaRepository.unidadesPorCategoria(any(), any())).thenReturn(List.of());
        when(detalleVentaRepository.topProductos(any(), any(), eq(5))).thenReturn(List.of());

        ProductosVendidosDTO r = service.productosVendidos(null, null);

        assertEquals(0, r.getTotalUnidades());
        assertTrue(r.getPorCategoria().isEmpty());
        assertTrue(r.getTopProductos().isEmpty());
    }

    @Test
    void productosVendidosConRangoInvalidoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.productosVendidos(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }

    // ---------- ingresoDiario ----------

    @Test
    void ingresoDiarioCalculaLaVariacionContraAyer() {
        LocalDate hoy = LocalDate.now();
        when(ventaRepository.ingresoEntre(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()))
                .thenReturn(new BigDecimal("150"));
        when(ventaRepository.ingresoEntre(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay()))
                .thenReturn(new BigDecimal("100"));

        IngresoDiarioDTO r = service.ingresoDiario();

        assertDecimal("150", r.getHoy());
        assertDecimal("100", r.getAyer());
        assertDecimal("50.0", r.getVariacionPct());
    }

    @Test
    void ingresoDiarioConCaidaDaVariacionNegativa() {
        LocalDate hoy = LocalDate.now();
        when(ventaRepository.ingresoEntre(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()))
                .thenReturn(new BigDecimal("50"));
        when(ventaRepository.ingresoEntre(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay()))
                .thenReturn(new BigDecimal("200"));

        assertDecimal("-75.0", service.ingresoDiario().getVariacionPct());
    }

    @Test
    void ingresoDiarioSinVentasAyerNoCalculaVariacion() {
        LocalDate hoy = LocalDate.now();
        when(ventaRepository.ingresoEntre(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay()))
                .thenReturn(new BigDecimal("80"));
        when(ventaRepository.ingresoEntre(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay()))
                .thenReturn(BigDecimal.ZERO);

        IngresoDiarioDTO r = service.ingresoDiario();

        assertNull(r.getVariacionPct());
        assertDecimal("80", r.getHoy());
    }

    // ---------- ingresoPromedio ----------

    @Test
    void ingresoPromedioCalculaPromedioDiarioYTicket() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 10);
        var d = desde.atStartOfDay();
        var h = hasta.plusDays(1).atStartOfDay();
        when(ventaRepository.ingresoEntre(d, h)).thenReturn(new BigDecimal("300"));
        when(ventaRepository.ordenesEntre(d, h)).thenReturn(3L);

        IngresoPromedioDTO r = service.ingresoPromedio(desde, hasta);

        assertEquals(10, r.getDias());
        assertEquals(3, r.getOrdenes());
        assertDecimal("300", r.getTotalIngreso());
        assertDecimal("30.00", r.getPromedioDiario());
        assertDecimal("100.00", r.getTicketPromedio());
    }

    @Test
    void ingresoPromedioDeUnSoloDiaCuentaUnDia() {
        LocalDate dia = LocalDate.of(2026, 11, 5);
        when(ventaRepository.ingresoEntre(any(), any())).thenReturn(new BigDecimal("90"));
        when(ventaRepository.ordenesEntre(any(), any())).thenReturn(2L);

        IngresoPromedioDTO r = service.ingresoPromedio(dia, dia);

        assertEquals(1, r.getDias());
        assertDecimal("90.00", r.getPromedioDiario());
        assertDecimal("45.00", r.getTicketPromedio());
    }

    @Test
    void ingresoPromedioSinOrdenesDejaElTicketEnCeroSinDividirPorCero() {
        when(ventaRepository.ingresoEntre(any(), any())).thenReturn(BigDecimal.ZERO);
        when(ventaRepository.ordenesEntre(any(), any())).thenReturn(0L);

        IngresoPromedioDTO r = service.ingresoPromedio(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));

        assertDecimal("0", r.getTicketPromedio());
        assertDecimal("0", r.getPromedioDiario());
        assertEquals(0, r.getOrdenes());
    }

    @Test
    void ingresoPromedioConRangoInvalidoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.ingresoPromedio(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }

    // ---------- concentracionIngresos ----------

    @Test
    void concentracionCalculaPorcentajesDelTopYDelResto() {
        LocalDate desde = LocalDate.of(2026, 11, 1);
        LocalDate hasta = LocalDate.of(2026, 11, 30);
        var d = desde.atStartOfDay();
        var h = hasta.plusDays(1).atStartOfDay();
        when(ventaRepository.ingresoEntre(d, h)).thenReturn(new BigDecimal("1000"));
        when(detalleVentaRepository.topProductosPorIngreso(d, h, 5)).thenReturn(List.of(
                agrupada(1, "Laptop X", 3, "400"),
                agrupada(2, "Mouse Y", 9, "300")));

        ConcentracionIngresosDTO r = service.concentracionIngresos(desde, hasta);

        assertDecimal("1000", r.getTotalIngreso());
        assertEquals(2, r.getProductos().size());
        assertDecimal("40.0", r.getProductos().get(0).getPorcentaje());
        assertDecimal("30.0", r.getProductos().get(1).getPorcentaje());
        assertDecimal("70.0", r.getTop5Pct());
        assertDecimal("300", r.getOtrosIngreso());
        assertDecimal("30.0", r.getOtrosPct());
    }

    @Test
    void concentracionSinIngresosDevuelveTodoEnCero() {
        when(ventaRepository.ingresoEntre(any(), any())).thenReturn(BigDecimal.ZERO);
        when(detalleVentaRepository.topProductosPorIngreso(any(), any(), eq(5))).thenReturn(List.of());

        ConcentracionIngresosDTO r = service.concentracionIngresos(null, null);

        assertTrue(r.getProductos().isEmpty());
        assertDecimal("0", r.getTop5Pct());
        assertDecimal("0", r.getOtrosIngreso());
        assertDecimal("0", r.getOtrosPct());
    }

    @Test
    void concentracionNuncaDevuelveOtrosNegativo() {
        when(ventaRepository.ingresoEntre(any(), any())).thenReturn(new BigDecimal("100"));
        when(detalleVentaRepository.topProductosPorIngreso(any(), any(), eq(5))).thenReturn(List.of(
                agrupada(1, "Laptop X", 1, "150")));

        ConcentracionIngresosDTO r = service.concentracionIngresos(null, null);

        assertDecimal("0", r.getOtrosIngreso());
    }

    @Test
    void concentracionConRangoInvalidoLanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> service.concentracionIngresos(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }
}
