package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import utp.mastertech.mastertech.dto.HuecoTarifaDTO;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.repository.ConfigEnvioRepository;
import utp.mastertech.mastertech.repository.DiaNoLaborableRepository;
import utp.mastertech.mastertech.repository.PuntoRepartoRepository;
import utp.mastertech.mastertech.repository.TarifaEnvioRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvioAdminServiceTest {

    @Mock
    private PuntoRepartoRepository puntoRepository;
    @Mock
    private TarifaEnvioRepository tarifaRepository;
    @Mock
    private ConfigEnvioRepository configRepository;
    @Mock
    private DiaNoLaborableRepository feriadoRepository;

    @InjectMocks
    private EnvioAdminService service;

    private TarifaEnvio tarifa(Long id, String nombre, String desde, String hasta, boolean activo) {
        TarifaEnvio t = new TarifaEnvio();
        t.setId(id);
        t.setNombre(nombre);
        t.setDistanciaDesdeKm(new BigDecimal(desde));
        t.setDistanciaHastaKm(new BigDecimal(hasta));
        t.setCosto(new BigDecimal("8.00"));
        t.setDiasEntregaMin(1);
        t.setDiasEntregaMax(1);
        t.setActivo(activo);
        return t;
    }

    // ---------- tarifas: validación de tramos ----------

    @Test
    void tramoContiguoNoSeConsideraSuperpuesto() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(tarifa(1L, "0 a 5 km", "0", "5", true)));
        TarifaEnvio nueva = tarifa(null, "5 a 10 km", "5", "10", true);
        when(tarifaRepository.save(nueva)).thenReturn(nueva);

        assertSame(nueva, service.guardarTarifa(nueva));
        verify(tarifaRepository).save(nueva);
    }

    @Test
    void tramoSuperpuestoConUnoActivoDevuelveConflicto() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(tarifa(1L, "0 a 5 km", "0", "5", true)));
        TarifaEnvio nueva = tarifa(null, "3 a 8 km", "3", "8", true);

        ConflictException ex = assertThrows(ConflictException.class, () -> service.guardarTarifa(nueva));

        assertTrue(ex.getMessage().contains("0 a 5 km"));
        verify(tarifaRepository, never()).save(any(TarifaEnvio.class));
    }

    @Test
    void tramoInactivoNoSeValidaContraLosDemas() {
        TarifaEnvio nueva = tarifa(null, "3 a 8 km", "3", "8", false);
        when(tarifaRepository.save(any(TarifaEnvio.class))).thenAnswer(i -> i.getArgument(0));

        service.guardarTarifa(nueva);

        verify(tarifaRepository, never()).findByActivoTrue();
    }

    @Test
    void editarUnTramoNoChocaConsigoMismo() {
        TarifaEnvio existente = tarifa(1L, "0 a 5 km", "0", "5", true);
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(existente));
        when(tarifaRepository.save(existente)).thenReturn(existente);
        existente.setDistanciaHastaKm(new BigDecimal("6"));

        assertDoesNotThrow(() -> service.guardarTarifa(existente));
    }

    @Test
    void distanciaMaximaIgualOMenorQueLaMinimaEsInvalida() {
        TarifaEnvio igual = tarifa(null, "x", "5", "5", true);
        TarifaEnvio menor = tarifa(null, "y", "5", "2", true);

        assertThrows(IllegalArgumentException.class, () -> service.guardarTarifa(igual));
        assertThrows(IllegalArgumentException.class, () -> service.guardarTarifa(menor));
        verify(tarifaRepository, never()).save(any(TarifaEnvio.class));
    }

    @Test
    void diasMaximosMenoresQueLosMinimosEsInvalido() {
        TarifaEnvio t = tarifa(null, "x", "0", "5", true);
        t.setDiasEntregaMin(3);
        t.setDiasEntregaMax(1);

        assertThrows(IllegalArgumentException.class, () -> service.guardarTarifa(t));
    }

    @Test
    void tarifaNuevaSinEstadoQuedaActiva() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of());
        when(tarifaRepository.save(any(TarifaEnvio.class))).thenAnswer(i -> i.getArgument(0));
        TarifaEnvio t = tarifa(null, "0 a 5 km", "0", "5", true);
        t.setActivo(null);

        assertTrue(service.guardarTarifa(t).getActivo());
    }

    @Test
    void activarUnTramoQueSeSuperponeDevuelveConflicto() {
        TarifaEnvio inactiva = tarifa(2L, "3 a 8 km", "3", "8", false);
        when(tarifaRepository.findById(2L)).thenReturn(Optional.of(inactiva));
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(tarifa(1L, "0 a 5 km", "0", "5", true)));

        assertThrows(ConflictException.class, () -> service.cambiarActivoTarifa(2L, true));
        verify(tarifaRepository, never()).save(any(TarifaEnvio.class));
    }

    @Test
    void desactivarUnTramoNoRequiereValidarSolapes() {
        TarifaEnvio activa = tarifa(1L, "0 a 5 km", "0", "5", true);
        when(tarifaRepository.findById(1L)).thenReturn(Optional.of(activa));
        when(tarifaRepository.save(activa)).thenReturn(activa);

        assertFalse(service.cambiarActivoTarifa(1L, false).getActivo());
        verify(tarifaRepository, never()).findByActivoTrue();
    }

    @Test
    void tarifaInexistenteDevuelveNotFound() {
        when(tarifaRepository.findById(9L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerTarifa(9L));
    }

    // ---------- tarifas: huecos de cobertura ----------

    @Test
    void sinHuecosCuandoLosTramosSonContiguosDesdeCero() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(
                tarifa(2L, "5 a 10 km", "5", "10", true),
                tarifa(1L, "0 a 5 km", "0", "5", true)));

        assertTrue(service.calcularHuecos().isEmpty());
    }

    @Test
    void detectaElHuecoEntreDosTramosAunqueVenganDesordenados() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(
                tarifa(2L, "10 a 20 km", "10", "20", true),
                tarifa(1L, "0 a 5 km", "0", "5", true),
                tarifa(3L, "20 a 30 km", "20", "30", true)));

        List<HuecoTarifaDTO> huecos = service.calcularHuecos();

        assertEquals(1, huecos.size());
        assertEquals(0, new BigDecimal("5").compareTo(huecos.get(0).getDesdeKm()));
        assertEquals(0, new BigDecimal("10").compareTo(huecos.get(0).getHastaKm()));
    }

    @Test
    void siElPrimerTramoNoEmpiezaEnCeroHayUnHuecoInicial() {
        when(tarifaRepository.findByActivoTrue()).thenReturn(List.of(tarifa(1L, "5 a 10 km", "5", "10", true)));

        List<HuecoTarifaDTO> huecos = service.calcularHuecos();

        assertEquals(1, huecos.size());
        assertEquals(0, BigDecimal.ZERO.compareTo(huecos.get(0).getDesdeKm()));
        assertEquals(0, new BigDecimal("5").compareTo(huecos.get(0).getHastaKm()));
    }

    // ---------- puntos de reparto ----------

    @Test
    void puntoNuevoSinEstadoQuedaActivoYConRecojo() {
        when(puntoRepository.save(any(PuntoReparto.class))).thenAnswer(i -> i.getArgument(0));
        PuntoReparto p = new PuntoReparto();
        p.setActivo(null);
        p.setPermiteRecojo(null);

        PuntoReparto guardado = service.guardarPunto(p);

        assertTrue(guardado.getActivo());
        assertTrue(guardado.getPermiteRecojo());
    }

    @Test
    void desactivarUnPuntoLoDejaInactivoSinBorrarlo() {
        PuntoReparto p = new PuntoReparto();
        p.setId(1L);
        p.setActivo(true);
        when(puntoRepository.findById(1L)).thenReturn(Optional.of(p));
        when(puntoRepository.save(p)).thenReturn(p);

        assertFalse(service.cambiarActivoPunto(1L, false).getActivo());
        verify(puntoRepository, never()).deleteById(any());
    }

    @Test
    void puntoInexistenteDevuelveNotFound() {
        when(puntoRepository.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.obtenerPunto(5L));
    }

    // ---------- configuración ----------

    @Test
    void sinFilaDeConfiguracionSeCreaConLosValoresPorDefecto() {
        when(configRepository.findById((short) 1)).thenReturn(Optional.empty());
        when(configRepository.save(any(ConfigEnvio.class))).thenAnswer(i -> i.getArgument(0));

        ConfigEnvio config = service.obtenerConfig();

        assertEquals(LocalTime.NOON, config.getHoraCorte());
        assertEquals("America/Lima", config.getZonaHoraria());
    }

    @Test
    void zonaHorariaInvalidaSeRechaza() {
        ConfigEnvio config = new ConfigEnvio();
        config.setZonaHoraria("Marte/Olympus");

        assertThrows(IllegalArgumentException.class, () -> service.guardarConfig(config));
        verify(configRepository, never()).save(any(ConfigEnvio.class));
    }

    @Test
    void guardarConfiguracionValidaConservaLaFilaUnica() {
        when(configRepository.save(any(ConfigEnvio.class))).thenAnswer(i -> i.getArgument(0));
        ConfigEnvio config = new ConfigEnvio();
        config.setId((short) 7);
        config.setZonaHoraria("America/Lima");

        ConfigEnvio guardada = service.guardarConfig(config);

        assertEquals(Short.valueOf((short) 1), guardada.getId());
    }

    // ---------- feriados ----------

    @Test
    void feriadoRepetidoDevuelveConflicto() {
        LocalDate fecha = LocalDate.of(2027, 12, 25);
        when(feriadoRepository.existsById(fecha)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.agregarFeriado(fecha, "Navidad"));
        verify(feriadoRepository, never()).save(any(DiaNoLaborable.class));
    }

    @Test
    void feriadoNuevoSeGuardaConLaDescripcionLimpia() {
        LocalDate fecha = LocalDate.of(2027, 2, 1);
        when(feriadoRepository.existsById(fecha)).thenReturn(false);
        when(feriadoRepository.save(any(DiaNoLaborable.class))).thenAnswer(i -> i.getArgument(0));

        DiaNoLaborable dia = service.agregarFeriado(fecha, "  Dia de prueba  ");

        assertEquals(fecha, dia.getFecha());
        assertEquals("Dia de prueba", dia.getDescripcion());
    }

    @Test
    void eliminarFeriadoInexistenteDevuelveNotFound() {
        LocalDate fecha = LocalDate.of(2027, 2, 1);
        when(feriadoRepository.existsById(fecha)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.eliminarFeriado(fecha));
        verify(feriadoRepository, never()).deleteById(any());
    }

    @Test
    void eliminarFeriadoExistenteLoBorra() {
        LocalDate fecha = LocalDate.of(2027, 2, 1);
        when(feriadoRepository.existsById(fecha)).thenReturn(true);

        service.eliminarFeriado(fecha);

        verify(feriadoRepository).deleteById(fecha);
    }
}
