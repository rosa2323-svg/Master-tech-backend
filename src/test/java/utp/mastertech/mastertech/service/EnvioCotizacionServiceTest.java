package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.repository.ConfigEnvioRepository;
import utp.mastertech.mastertech.repository.DiaNoLaborableRepository;
import utp.mastertech.mastertech.repository.PuntoRepartoRepository;
import utp.mastertech.mastertech.repository.TarifaEnvioRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvioCotizacionServiceTest {

    @Mock
    private PuntoRepartoRepository puntoRepartoRepository;
    @Mock
    private TarifaEnvioRepository tarifaEnvioRepository;
    @Mock
    private ConfigEnvioRepository configEnvioRepository;
    @Mock
    private DiaNoLaborableRepository diaNoLaborableRepository;

    @InjectMocks
    private EnvioCotizacionService service;

    // Miércoles 25/11/2026 10:00 en Lima (UTC-5), antes del corte de las 12:00
    private static final Instant MIE_10AM = Instant.parse("2026-11-25T15:00:00Z");

    private PuntoReparto sede(long id, String nombre, String lat, String lng) {
        PuntoReparto p = new PuntoReparto();
        p.setId(id);
        p.setNombre(nombre);
        p.setLatitud(new BigDecimal(lat));
        p.setLongitud(new BigDecimal(lng));
        return p;
    }

    private TarifaEnvio tarifa(String nombre, int desde, int hasta, String costo, String gratisDesde, int min, int max) {
        TarifaEnvio t = new TarifaEnvio();
        t.setNombre(nombre);
        t.setDistanciaDesdeKm(BigDecimal.valueOf(desde));
        t.setDistanciaHastaKm(BigDecimal.valueOf(hasta));
        t.setCosto(new BigDecimal(costo));
        t.setEnvioGratisDesde(gratisDesde == null ? null : new BigDecimal(gratisDesde));
        t.setDiasEntregaMin(min);
        t.setDiasEntregaMax(max);
        return t;
    }

    private void calendario(int diasPreparacion, LocalDate... feriados) {
        ConfigEnvio c = new ConfigEnvio();
        c.setDiasPreparacion(diasPreparacion);
        when(configEnvioRepository.findById((short) 1)).thenReturn(Optional.of(c));
        List<DiaNoLaborable> lista = new java.util.ArrayList<>();
        for (LocalDate f : feriados) {
            DiaNoLaborable d = new DiaNoLaborable();
            d.setFecha(f);
            d.setDescripcion("feriado");
            lista.add(d);
        }
        when(diaNoLaborableRepository.findAll()).thenReturn(lista);
    }

    // ---------- utilidades puras ----------

    @Test
    void distanciaEntreElCentroYMirafloresEsDeAlrededorDe8Km() {
        double km = EnvioCotizacionService.distanciaKm(-12.0464, -77.0428, -12.1211, -77.0297);

        assertTrue(km > 8.0 && km < 9.0, "km = " + km);
    }

    @Test
    void distanciaEntreElMismoPuntoEsCero() {
        assertEquals(0.0, EnvioCotizacionService.distanciaKm(-12.0, -77.0, -12.0, -77.0), 1e-9);
    }

    @Test
    void domingosYFeriadosNoSonHabilesPeroSabadosSi() {
        Set<LocalDate> feriados = Set.of(LocalDate.of(2026, 12, 25));

        assertTrue(EnvioCotizacionService.esHabil(LocalDate.of(2026, 11, 28), feriados));   // sábado
        assertFalse(EnvioCotizacionService.esHabil(LocalDate.of(2026, 11, 29), feriados));  // domingo
        assertFalse(EnvioCotizacionService.esHabil(LocalDate.of(2026, 12, 25), feriados));  // viernes feriado
        assertTrue(EnvioCotizacionService.esHabil(LocalDate.of(2026, 11, 25), feriados));   // miércoles
    }

    @Test
    void sumarHabilesSaltaDomingosYFeriados() {
        Set<LocalDate> sinFeriados = Set.of();
        Set<LocalDate> lunesFeriado = Set.of(LocalDate.of(2026, 11, 30));
        LocalDate sabado = LocalDate.of(2026, 11, 28);

        assertEquals(sabado, EnvioCotizacionService.sumarHabiles(sabado, 0, sinFeriados));
        assertEquals(LocalDate.of(2026, 11, 30), EnvioCotizacionService.sumarHabiles(sabado, 1, sinFeriados));
        assertEquals(LocalDate.of(2026, 12, 1), EnvioCotizacionService.sumarHabiles(sabado, 1, lunesFeriado));
    }

    // ---------- estimarEntrega ----------

    @Test
    void antesDelCorteElPlazoCuentaDesdeElMismoDia() {
        calendario(0);

        EnvioCotizacionService.VentanaEntrega v = service.estimarEntrega(1, 2, MIE_10AM);

        assertEquals(LocalDate.of(2026, 11, 26), v.desde());
        assertEquals(LocalDate.of(2026, 11, 27), v.hasta());
    }

    @Test
    void despuesDelCorteElPlazoCuentaDesdeElSiguienteDiaHabil() {
        calendario(0);
        Instant mie1pm = Instant.parse("2026-11-25T18:00:00Z");

        EnvioCotizacionService.VentanaEntrega v = service.estimarEntrega(1, 2, mie1pm);

        assertEquals(LocalDate.of(2026, 11, 27), v.desde());
        assertEquals(LocalDate.of(2026, 11, 28), v.hasta());
    }

    @Test
    void justoALaHoraDeCorteYaCuentaComoDespues() {
        calendario(0);
        Instant mie12 = Instant.parse("2026-11-25T17:00:00Z");

        assertEquals(LocalDate.of(2026, 11, 27), service.estimarEntrega(1, 1, mie12).desde());
    }

    @Test
    void unPagoEnDomingoCuentaDesdeElLunes() {
        calendario(0);
        Instant domingo10am = Instant.parse("2026-11-29T15:00:00Z");

        EnvioCotizacionService.VentanaEntrega v = service.estimarEntrega(1, 1, domingo10am);

        assertEquals(LocalDate.of(2026, 12, 1), v.desde());   // lunes 30 cuenta como día 0
    }

    @Test
    void laHoraDeCorteSeEvaluaEnLimaNoEnUtc() {
        calendario(0);
        // 23:30 en Lima del miércoles = jueves 04:30 UTC: debe tratarse como miércoles después del corte
        Instant mie1130pm = Instant.parse("2026-11-26T04:30:00Z");

        assertEquals(LocalDate.of(2026, 11, 27), service.estimarEntrega(1, 1, mie1130pm).desde());
    }

    @Test
    void losFeriadosNoCuentanYLosDiasDePreparacionSeSuman() {
        // lunes 07/12/2026 10:00 Lima; 8 y 9 de diciembre feriados; 1 día de preparación
        calendario(1, LocalDate.of(2026, 12, 8), LocalDate.of(2026, 12, 9));
        Instant lun10am = Instant.parse("2026-12-07T15:00:00Z");

        EnvioCotizacionService.VentanaEntrega v = service.estimarEntrega(1, 1, lun10am);

        // salida = jueves 10 (prep 1 hábil desde el lunes), entrega = viernes 11
        assertEquals(LocalDate.of(2026, 12, 11), v.desde());
    }

    @Test
    void sinFilaDeConfiguracionUsaLosValoresPorDefecto() {
        when(configEnvioRepository.findById((short) 1)).thenReturn(Optional.empty());
        when(diaNoLaborableRepository.findAll()).thenReturn(List.of());

        assertEquals(LocalDate.of(2026, 11, 26), service.estimarEntrega(1, 1, MIE_10AM).desde());
    }

    // ---------- cotizar ----------

    @Test
    void cotizaConLaSedeMasCercanaYSuTarifa() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(
                sede(1, "Norte", "-11.980000", "-77.070000"),
                sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(
                tarifa("0 a 5 km", 0, 5, "8.00", "1500.00", 1, 1),
                tarifa("5 a 10 km", 5, 10, "12.00", "1500.00", 1, 1)));
        calendario(0);

        EnvioCotizacionService.Cotizacion c = service.cotizar(
                new BigDecimal("200.00"), new BigDecimal("-12.150000"), new BigDecimal("-77.020000"), MIE_10AM);

        assertTrue(c.cubierto());
        assertEquals("Sur", c.sede().getNombre());
        assertEquals("0 a 5 km", c.tarifa().getNombre());
        assertEquals(0, new BigDecimal("8.00").compareTo(c.costoEnvio()));
        assertFalse(c.gratis());
        assertTrue(c.distanciaKm().compareTo(BigDecimal.ZERO) > 0 && c.distanciaKm().compareTo(new BigDecimal("5")) < 0);
        assertEquals(LocalDate.of(2026, 11, 26), c.entrega().desde());
    }

    @Test
    void elEnvioEsGratisAlLlegarAlMontoDeLaTarifa() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(tarifa("0 a 5 km", 0, 5, "8.00", "1500.00", 1, 1)));
        calendario(0);

        EnvioCotizacionService.Cotizacion c = service.cotizar(
                new BigDecimal("1500.00"), new BigDecimal("-12.150000"), new BigDecimal("-77.020000"), MIE_10AM);

        assertTrue(c.gratis());
        assertEquals(0, BigDecimal.ZERO.compareTo(c.costoEnvio()));
    }

    @Test
    void justoDebajoDelMontoGratisSiSeCobra() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(tarifa("0 a 5 km", 0, 5, "8.00", "1500.00", 1, 1)));
        calendario(0);

        EnvioCotizacionService.Cotizacion c = service.cotizar(
                new BigDecimal("1499.99"), new BigDecimal("-12.150000"), new BigDecimal("-77.020000"), MIE_10AM);

        assertFalse(c.gratis());
        assertEquals(0, new BigDecimal("8.00").compareTo(c.costoEnvio()));
    }

    @Test
    void unaTarifaSinMontoGratisNuncaEsGratis() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(tarifa("0 a 5 km", 0, 5, "8.00", null, 1, 1)));
        calendario(0);

        assertFalse(service.cotizar(new BigDecimal("99999"), new BigDecimal("-12.150000"),
                new BigDecimal("-77.020000"), MIE_10AM).gratis());
    }

    @Test
    void sinSedesActivasNoHayCobertura() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of());

        EnvioCotizacionService.Cotizacion c = service.cotizar(
                BigDecimal.TEN, new BigDecimal("-12.15"), new BigDecimal("-77.02"), MIE_10AM);

        assertFalse(c.cubierto());
        assertNotNull(c.mensaje());
        verifyNoInteractions(tarifaEnvioRepository);
    }

    @Test
    void unaDireccionLejosDeLimaNoTieneCobertura() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(tarifa("30 a 45 km", 30, 45, "35.00", null, 2, 3)));

        EnvioCotizacionService.Cotizacion c = service.cotizar(
                BigDecimal.TEN, new BigDecimal("-16.400000"), new BigDecimal("-71.500000"), MIE_10AM);   // Arequipa

        assertFalse(c.cubierto());
        assertEquals("Todavía no llegamos a esa dirección.", c.mensaje());
    }

    @Test
    void unHuecoEntreTarifasTampocoTieneCobertura() {
        when(puntoRepartoRepository.findByActivoTrue()).thenReturn(List.of(sede(3, "Sur", "-12.170000", "-77.010000")));
        when(tarifaEnvioRepository.findByActivoTrue()).thenReturn(List.of(tarifa("10 a 20 km", 10, 20, "18.00", null, 1, 2)));

        // a ~2,5 km de la sede, sin tarifa activa para ese tramo
        assertFalse(service.cotizar(BigDecimal.TEN, new BigDecimal("-12.150000"),
                new BigDecimal("-77.020000"), MIE_10AM).cubierto());
    }
}

