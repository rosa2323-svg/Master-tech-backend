package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import utp.mastertech.mastertech.dto.TendenciaPuntoDTO;
import utp.mastertech.mastertech.repository.TendenciaVentaProjection;
import utp.mastertech.mastertech.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DashboardService {

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd MMM", new Locale("es", "PE"));
    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "PE"));

    @Autowired
    private VentaRepository ventaRepository;

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