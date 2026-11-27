package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.repository.ConfigEnvioRepository;
import utp.mastertech.mastertech.repository.DiaNoLaborableRepository;
import utp.mastertech.mastertech.repository.PuntoRepartoRepository;
import utp.mastertech.mastertech.repository.TarifaEnvioRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cotiza envíos a domicilio: sede activa más cercana, tarifa por tramo de distancia,
 * envío gratis por monto y fecha estimada en días hábiles (lunes a sábado, sin feriados).
 *
 * La sede se elige por distancia en línea recta (Haversine) y el kilometraje que se cobra es el de la ruta
 * real de Google cuando está disponible; si no, el de línea recta.
 */
@Service
public class EnvioCotizacionService {

    private static final double RADIO_TIERRA_KM = 6371.0088;
    private static final int MAX_DIAS_BUSQUEDA = 366;

    @Autowired
    private DistanciaRutaService distanciaRutaService;

    @Autowired
    private PuntoRepartoRepository puntoRepartoRepository;

    @Autowired
    private TarifaEnvioRepository tarifaEnvioRepository;

    @Autowired
    private ConfigEnvioRepository configEnvioRepository;

    @Autowired
    private DiaNoLaborableRepository diaNoLaborableRepository;

    /** Rango de fechas en que llegaría el pedido. */
    public record VentanaEntrega(LocalDate desde, LocalDate hasta) {
    }

    public record Cotizacion(boolean cubierto, String mensaje, BigDecimal costoEnvio, boolean gratis,
                             BigDecimal distanciaKm, PuntoReparto sede, TarifaEnvio tarifa,
                             VentanaEntrega entrega) {
        static Cotizacion sinCobertura(String mensaje) {
            return new Cotizacion(false, mensaje, null, false, null, null, null, null);
        }
    }

    /**
     * @param subtotal monto de los productos (sin envío), para decidir si el envío es gratis
     * @param ahora    momento de la compra, para aplicar la hora de corte
     */
    public Cotizacion cotizar(BigDecimal subtotal, BigDecimal latitud, BigDecimal longitud, Instant ahora) {
        List<PuntoReparto> sedes = puntoRepartoRepository.findByActivoTrue();
        if (sedes.isEmpty()) {
            return Cotizacion.sinCobertura("Por ahora no hay almacenes disponibles para entregas a domicilio.");
        }

        PuntoReparto cercana = null;
        double mejor = Double.MAX_VALUE;
        for (PuntoReparto sede : sedes) {
            double d = distanciaKm(latitud.doubleValue(), longitud.doubleValue(),
                    sede.getLatitud().doubleValue(), sede.getLongitud().doubleValue());
            if (d < mejor) {
                mejor = d;
                cercana = sede;
            }
        }
        BigDecimal enLinea = BigDecimal.valueOf(mejor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal km = distanciaRutaService.distanciaPorRutaKm(cercana, latitud, longitud).orElse(enLinea);

        TarifaEnvio tarifa = tarifaEnvioRepository.findByActivoTrue().stream()
                .filter(t -> km.compareTo(t.getDistanciaDesdeKm()) >= 0 && km.compareTo(t.getDistanciaHastaKm()) < 0)
                .findFirst()
                .orElse(null);
        if (tarifa == null) {
            return Cotizacion.sinCobertura("Todavía no llegamos a esa dirección.");
        }

        boolean gratis = tarifa.getEnvioGratisDesde() != null
                && subtotal.compareTo(tarifa.getEnvioGratisDesde()) >= 0;
        BigDecimal costo = gratis ? BigDecimal.ZERO.setScale(2) : tarifa.getCosto();

        VentanaEntrega ventana = estimarEntrega(tarifa.getDiasEntregaMin(), tarifa.getDiasEntregaMax(), ahora);
        return new Cotizacion(true, null, costo, gratis, km, cercana, tarifa, ventana);
    }

    /** Fechas de entrega más temprana y más tardía para un plazo en días hábiles. */
    public VentanaEntrega estimarEntrega(int diasMin, int diasMax, Instant ahora) {
        ConfigEnvio config = configEnvioRepository.findById((short) 1).orElseGet(ConfigEnvio::new);
        Set<LocalDate> feriados = new HashSet<>();
        for (DiaNoLaborable d : diaNoLaborableRepository.findAll()) {
            feriados.add(d.getFecha());
        }
        ZonedDateTime local = ahora.atZone(ZoneId.of(config.getZonaHoraria()));

        LocalDate inicio = local.toLocalDate();
        if (!esHabil(inicio, feriados) || !local.toLocalTime().isBefore(config.getHoraCorte())) {
            inicio = siguienteHabil(inicio, feriados);
        }
        LocalDate salida = sumarHabiles(inicio, config.getDiasPreparacion(), feriados);
        return new VentanaEntrega(sumarHabiles(salida, diasMin, feriados), sumarHabiles(salida, diasMax, feriados));
    }

    // ---- utilidades puras (visibles para pruebas) ----

    static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return RADIO_TIERRA_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    static boolean esHabil(LocalDate fecha, Set<LocalDate> feriados) {
        return fecha.getDayOfWeek() != DayOfWeek.SUNDAY && !feriados.contains(fecha);
    }

    static LocalDate siguienteHabil(LocalDate fecha, Set<LocalDate> feriados) {
        LocalDate f = fecha;
        for (int i = 0; i < MAX_DIAS_BUSQUEDA; i++) {
            f = f.plusDays(1);
            if (esHabil(f, feriados)) return f;
        }
        throw new IllegalStateException("No se encontró un día hábil: revisa los feriados configurados.");
    }

    static LocalDate sumarHabiles(LocalDate fecha, int dias, Set<LocalDate> feriados) {
        LocalDate f = fecha;
        for (int i = 0; i < dias; i++) {
            f = siguienteHabil(f, feriados);
        }
        return f;
    }
}
