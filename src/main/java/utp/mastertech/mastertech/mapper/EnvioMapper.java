package utp.mastertech.mastertech.mapper;

import org.springframework.stereotype.Component;
import utp.mastertech.mastertech.dto.ConfigEnvioDTO;
import utp.mastertech.mastertech.dto.DiaNoLaborableDTO;
import utp.mastertech.mastertech.dto.PuntoRepartoDTO;
import utp.mastertech.mastertech.dto.TarifaEnvioDTO;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;

/** Conversiones entidad ↔ DTO de la configuración de envíos (sedes, tarifas, calendario). */
@Component
public class EnvioMapper {

    private String limpiar(String texto) {
        return texto == null ? null : texto.trim();
    }

    // ---------- Puntos de reparto ----------

    public PuntoRepartoDTO toDTO(PuntoReparto punto) {
        if (punto == null) return null;

        PuntoRepartoDTO dto = new PuntoRepartoDTO();
        dto.setId(punto.getId());
        dto.setNombre(punto.getNombre());
        dto.setDireccion(punto.getDireccion());
        dto.setLatitud(punto.getLatitud());
        dto.setLongitud(punto.getLongitud());
        dto.setPermiteRecojo(punto.getPermiteRecojo());
        dto.setActivo(punto.getActivo());
        return dto;
    }

    public PuntoReparto toEntity(PuntoRepartoDTO dto) {
        if (dto == null) return null;

        PuntoReparto punto = new PuntoReparto();
        punto.setId(dto.getId());
        updateEntityFromDTO(dto, punto);
        return punto;
    }

    public void updateEntityFromDTO(PuntoRepartoDTO dto, PuntoReparto punto) {
        punto.setNombre(limpiar(dto.getNombre()));
        punto.setDireccion(limpiar(dto.getDireccion()));
        punto.setLatitud(dto.getLatitud());
        punto.setLongitud(dto.getLongitud());
        if (dto.getPermiteRecojo() != null) punto.setPermiteRecojo(dto.getPermiteRecojo());
        if (dto.getActivo() != null) punto.setActivo(dto.getActivo());
    }

    // ---------- Tarifas ----------

    public TarifaEnvioDTO toDTO(TarifaEnvio tarifa) {
        if (tarifa == null) return null;

        TarifaEnvioDTO dto = new TarifaEnvioDTO();
        dto.setId(tarifa.getId());
        dto.setNombre(tarifa.getNombre());
        dto.setDistanciaDesdeKm(tarifa.getDistanciaDesdeKm());
        dto.setDistanciaHastaKm(tarifa.getDistanciaHastaKm());
        dto.setCosto(tarifa.getCosto());
        dto.setEnvioGratisDesde(tarifa.getEnvioGratisDesde());
        dto.setDiasEntregaMin(tarifa.getDiasEntregaMin());
        dto.setDiasEntregaMax(tarifa.getDiasEntregaMax());
        dto.setActivo(tarifa.getActivo());
        return dto;
    }

    public TarifaEnvio toEntity(TarifaEnvioDTO dto) {
        if (dto == null) return null;

        TarifaEnvio tarifa = new TarifaEnvio();
        tarifa.setId(dto.getId());
        updateEntityFromDTO(dto, tarifa);
        return tarifa;
    }

    public void updateEntityFromDTO(TarifaEnvioDTO dto, TarifaEnvio tarifa) {
        tarifa.setNombre(limpiar(dto.getNombre()));
        tarifa.setDistanciaDesdeKm(dto.getDistanciaDesdeKm());
        tarifa.setDistanciaHastaKm(dto.getDistanciaHastaKm());
        tarifa.setCosto(dto.getCosto());
        tarifa.setEnvioGratisDesde(dto.getEnvioGratisDesde()); // vacío = nunca es gratis (puede limpiarse)
        tarifa.setDiasEntregaMin(dto.getDiasEntregaMin());
        tarifa.setDiasEntregaMax(dto.getDiasEntregaMax());
        if (dto.getActivo() != null) tarifa.setActivo(dto.getActivo());
    }

    // ---------- Configuración ----------

    public ConfigEnvioDTO toDTO(ConfigEnvio config) {
        if (config == null) return null;

        ConfigEnvioDTO dto = new ConfigEnvioDTO();
        dto.setHoraCorte(config.getHoraCorte());
        dto.setDiasPreparacion(config.getDiasPreparacion());
        dto.setZonaHoraria(config.getZonaHoraria());
        return dto;
    }

    public void updateEntityFromDTO(ConfigEnvioDTO dto, ConfigEnvio config) {
        config.setHoraCorte(dto.getHoraCorte());
        config.setDiasPreparacion(dto.getDiasPreparacion());
        config.setZonaHoraria(limpiar(dto.getZonaHoraria()));
    }

    // ---------- Feriados ----------

    public DiaNoLaborableDTO toDTO(DiaNoLaborable dia) {
        if (dia == null) return null;

        DiaNoLaborableDTO dto = new DiaNoLaborableDTO();
        dto.setFecha(dia.getFecha());
        dto.setDescripcion(dia.getDescripcion());
        return dto;
    }
}
