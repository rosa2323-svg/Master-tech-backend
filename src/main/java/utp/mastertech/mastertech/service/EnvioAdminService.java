package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Administración de la configuración de envíos: almacenes, tarifas por tramo de distancia,
 * hora de corte y feriados. Nada se borra salvo los feriados: sedes y tarifas se desactivan.
 */
@Service
public class EnvioAdminService {

    private static final Short ID_CONFIG = (short) 1;

    @Autowired
    private PuntoRepartoRepository puntoRepository;

    @Autowired
    private TarifaEnvioRepository tarifaRepository;

    @Autowired
    private ConfigEnvioRepository configRepository;

    @Autowired
    private DiaNoLaborableRepository feriadoRepository;

    // ---------------------------------------------------------------- puntos de reparto

    public List<PuntoReparto> listarPuntos() {
        return puntoRepository.findAllByOrderByIdAsc();
    }

    public PuntoReparto obtenerPunto(Long id) {
        return puntoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Punto de reparto no encontrado: " + id));
    }

    @Transactional
    public PuntoReparto guardarPunto(PuntoReparto punto) {
        if (punto.getActivo() == null) punto.setActivo(true);
        if (punto.getPermiteRecojo() == null) punto.setPermiteRecojo(true);
        return puntoRepository.save(punto);
    }

    @Transactional
    public PuntoReparto cambiarActivoPunto(Long id, boolean activo) {
        PuntoReparto punto = obtenerPunto(id);
        punto.setActivo(activo);
        return puntoRepository.save(punto);
    }

    // ---------------------------------------------------------------- tarifas

    public List<TarifaEnvio> listarTarifas() {
        return tarifaRepository.findAllByOrderByDistanciaDesdeKmAscIdAsc();
    }

    public TarifaEnvio obtenerTarifa(Long id) {
        return tarifaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tarifa de envío no encontrada: " + id));
    }

    /** Valida y guarda un tramo (alta o edición). */
    @Transactional
    public TarifaEnvio guardarTarifa(TarifaEnvio tarifa) {
        if (tarifa.getActivo() == null) tarifa.setActivo(true);
        validarTarifa(tarifa);
        return tarifaRepository.save(tarifa);
    }

    /** Activar un tramo vuelve a validar que no se superponga con otro activo. */
    @Transactional
    public TarifaEnvio cambiarActivoTarifa(Long id, boolean activo) {
        TarifaEnvio tarifa = obtenerTarifa(id);
        tarifa.setActivo(activo);
        validarTarifa(tarifa);
        return tarifaRepository.save(tarifa);
    }

    /**
     * Reglas de un tramo [desde, hasta): el límite superior es mayor que el inferior, los días
     * máximos no son menores que los mínimos y, si está activo, no se superpone con otro activo.
     * Dos tramos contiguos (0-5 y 5-10) no se superponen.
     */
    void validarTarifa(TarifaEnvio tarifa) {
        if (tarifa.getDistanciaHastaKm().compareTo(tarifa.getDistanciaDesdeKm()) <= 0) {
            throw new IllegalArgumentException("La distancia máxima debe ser mayor que la mínima.");
        }
        if (tarifa.getDiasEntregaMax() < tarifa.getDiasEntregaMin()) {
            throw new IllegalArgumentException("Los días máximos de entrega no pueden ser menores que los mínimos.");
        }
        if (!Boolean.TRUE.equals(tarifa.getActivo())) {
            return;
        }
        for (TarifaEnvio otra : tarifaRepository.findByActivoTrue()) {
            if (Objects.equals(otra.getId(), tarifa.getId())) continue;
            boolean seSuperpone = tarifa.getDistanciaDesdeKm().compareTo(otra.getDistanciaHastaKm()) < 0
                    && otra.getDistanciaDesdeKm().compareTo(tarifa.getDistanciaHastaKm()) < 0;
            if (seSuperpone) {
                throw new ConflictException("El tramo se superpone con \"" + otra.getNombre() + "\" ("
                        + km(otra.getDistanciaDesdeKm()) + " a " + km(otra.getDistanciaHastaKm()) + " km).");
            }
        }
    }

    /** Rangos de distancia, desde 0 km hasta el último tramo activo, que ninguna tarifa activa cubre. */
    public List<HuecoTarifaDTO> calcularHuecos() {
        List<TarifaEnvio> activas = new ArrayList<>(tarifaRepository.findByActivoTrue());
        activas.sort(Comparator.comparing(TarifaEnvio::getDistanciaDesdeKm));

        List<HuecoTarifaDTO> huecos = new ArrayList<>();
        BigDecimal cursor = BigDecimal.ZERO;
        for (TarifaEnvio tramo : activas) {
            if (tramo.getDistanciaDesdeKm().compareTo(cursor) > 0) {
                huecos.add(new HuecoTarifaDTO(cursor, tramo.getDistanciaDesdeKm()));
            }
            if (tramo.getDistanciaHastaKm().compareTo(cursor) > 0) {
                cursor = tramo.getDistanciaHastaKm();
            }
        }
        return huecos;
    }

    private String km(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString();
    }

    // ---------------------------------------------------------------- configuración

    /** La fila única (id = 1) la crea la migración; si faltara, se crea con los valores por defecto. */
    @Transactional
    public ConfigEnvio obtenerConfig() {
        return configRepository.findById(ID_CONFIG)
                .orElseGet(() -> configRepository.save(new ConfigEnvio()));
    }

    @Transactional
    public ConfigEnvio guardarConfig(ConfigEnvio config) {
        try {
            ZoneId.of(config.getZonaHoraria());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Zona horaria no válida: " + config.getZonaHoraria());
        }
        config.setId(ID_CONFIG);
        return configRepository.save(config);
    }

    // ---------------------------------------------------------------- feriados

    public List<DiaNoLaborable> listarFeriados() {
        return feriadoRepository.findAllByOrderByFechaAsc();
    }

    @Transactional
    public DiaNoLaborable agregarFeriado(LocalDate fecha, String descripcion) {
        if (feriadoRepository.existsById(fecha)) {
            throw new ConflictException("Ya hay un feriado registrado en esa fecha.");
        }
        DiaNoLaborable dia = new DiaNoLaborable();
        dia.setFecha(fecha);
        dia.setDescripcion(descripcion.trim());
        return feriadoRepository.save(dia);
    }

    @Transactional
    public void eliminarFeriado(LocalDate fecha) {
        if (!feriadoRepository.existsById(fecha)) {
            throw new ResourceNotFoundException("No hay un feriado registrado en esa fecha.");
        }
        feriadoRepository.deleteById(fecha);
    }
}

