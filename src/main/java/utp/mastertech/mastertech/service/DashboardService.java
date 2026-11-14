package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import utp.mastertech.mastertech.dto.IngresoDiarioDTO;
import utp.mastertech.mastertech.dto.ProductosVendidosDTO;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.TendenciaVentaProjection;
import utp.mastertech.mastertech.repository.VentaRepository;
import utp.mastertech.mastertech.dto.ConcentracionIngresosDTO;
import utp.mastertech.mastertech.dto.IngresoPromedioDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.temporal.ChronoUnit;

@Service
public class DashboardService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd MMM", new Locale("es", "PE"));
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "PE"));

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private DetalleVentaRepository detalleVentaRepository;

    /** Serie de ventas agrupada por dia | semana | mes, con ceros en los periodos sin ventas. */
    public List<TendenciaPuntoDTO> tendenciaVentas(LocalDate desde, LocalDate hasta, String agrupar) {
        String modo = agrupar == null ? "dia" : agrupar.toLowerCase();
        String granularidad = switch (modo) {
            case "dia" -> "day";
            case "semana" -> "week";
            case "mes" -> "month";
            default -> throw new IllegalArgumentException("El parámetro 'agrupar' debe ser dia, semana o mes");
        };

        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        if (ini.isAfter(fin)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }

        Map<LocalDate, TendenciaVentaProjection> porPeriodo = new HashMap<>();
        ventaRepository.tendenciaVentas(granularidad, ini.atStartOfDay(), fin.plusDays(1).atStartOfDay())
                .forEach(p -> porPeriodo.put(p.getPeriodo(), p));

        List<TendenciaPuntoDTO> serie = new ArrayList<>();
        LocalDate cursor = truncar(ini, modo);
        while (!cursor.isAfter(fin)) {
            TendenciaVentaProjection p = porPeriodo.get(cursor);
            serie.add(new TendenciaPuntoDTO(
                    cursor,
                    etiqueta(cursor, modo),
                    p != null ? p.getOrdenes() : 0L,
                    p != null ? p.getUnidades() : 0L,
                    p != null ? p.getIngreso() : BigDecimal.ZERO));
            cursor = switch (modo) {
                case "semana" -> cursor.plusWeeks(1);
                case "mes" -> cursor.plusMonths(1);
                default -> cursor.plusDays(1);
            };
        }
        return serie;
    }

    /** Unidades vendidas del periodo, segmentadas por categoría y con el top 5 de productos. */
    public ProductosVendidosDTO productosVendidos(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        if (ini.isAfter(fin)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        var d = ini.atStartOfDay();
        var h = fin.plusDays(1).atStartOfDay();

        List<ProductosVendidosDTO.Item> categorias = detalleVentaRepository.unidadesPorCategoria(d, h).stream()
                .map(p -> new ProductosVendidosDTO.Item(p.getId(), p.getNombre(), p.getUnidades(), p.getIngreso()))
                .toList();
        List<ProductosVendidosDTO.Item> top = detalleVentaRepository.topProductos(d, h, 5).stream()
                .map(p -> new ProductosVendidosDTO.Item(p.getId(), p.getNombre(), p.getUnidades(), p.getIngreso()))
                .toList();

        long total = categorias.stream().mapToLong(ProductosVendidosDTO.Item::getUnidades).sum();
        return new ProductosVendidosDTO(total, categorias, top);
    }

    /** Ingreso de hoy, el de ayer y la variación porcentual entre ambos. */
    public IngresoDiarioDTO ingresoDiario() {
        LocalDate hoy = LocalDate.now();
        BigDecimal ingresoHoy = ventaRepository.ingresoEntre(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        BigDecimal ingresoAyer = ventaRepository.ingresoEntre(hoy.minusDays(1).atStartOfDay(), hoy.atStartOfDay());

        BigDecimal variacion = ingresoAyer.signum() == 0
                ? null
                : ingresoHoy.subtract(ingresoAyer)
                .multiply(BigDecimal.valueOf(100))
                .divide(ingresoAyer, 1, RoundingMode.HALF_UP);

        return new IngresoDiarioDTO(ingresoHoy, ingresoAyer, variacion);
    }

    public IngresoPromedioDTO ingresoPromedio(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        if (ini.isAfter(fin)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        var d = ini.atStartOfDay();
        var h = fin.plusDays(1).atStartOfDay();

        BigDecimal total = ventaRepository.ingresoEntre(d, h);
        long ordenes = ventaRepository.ordenesEntre(d, h);
        long dias = ChronoUnit.DAYS.between(ini, fin) + 1;

        BigDecimal promedioDiario = total.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP);
        BigDecimal ticket = ordenes == 0
                ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(ordenes), 2, RoundingMode.HALF_UP);

        return new IngresoPromedioDTO(total, ordenes, dias, promedioDiario, ticket);
    }

    public ConcentracionIngresosDTO concentracionIngresos(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now();
        LocalDate ini = desde != null ? desde : fin.minusDays(29);
        if (ini.isAfter(fin)) {
            throw new IllegalArgumentException("'desde' no puede ser posterior a 'hasta'");
        }
        var d = ini.atStartOfDay();
        var h = fin.plusDays(1).atStartOfDay();

        BigDecimal total = ventaRepository.ingresoEntre(d, h);

        List<ConcentracionIngresosDTO.Item> productos = detalleVentaRepository.topProductosPorIngreso(d, h, 5).stream()
                .map(p -> new ConcentracionIngresosDTO.Item(
                        p.getId(), p.getNombre(), p.getIngreso(), porcentaje(p.getIngreso(), total)))
                .toList();

        BigDecimal ingresoTop = productos.stream()
                .map(ConcentracionIngresosDTO.Item::getIngreso)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otros = total.subtract(ingresoTop).max(BigDecimal.ZERO);

        return new ConcentracionIngresosDTO(
                total, porcentaje(ingresoTop, total), productos, otros, porcentaje(otros, total));
    }

    private BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private LocalDate truncar(LocalDate fecha, String modo) {
        return switch (modo) {
            case "semana" -> fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case "mes" -> fecha.withDayOfMonth(1);
            default -> fecha;
        };
    }

    private String etiqueta(LocalDate fecha, String modo) {
        return "mes".equals(modo) ? fecha.format(MES) : fecha.format(DIA);
    }
}