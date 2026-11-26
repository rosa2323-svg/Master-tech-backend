package utp.mastertech.mastertech.mapper;

import org.junit.jupiter.api.Test;
import utp.mastertech.mastertech.dto.ConfigEnvioDTO;
import utp.mastertech.mastertech.dto.PuntoRepartoDTO;
import utp.mastertech.mastertech.dto.TarifaEnvioDTO;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;

import java.math.BigDecimal;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class EnvioMapperTest {

    private final EnvioMapper mapper = new EnvioMapper();

    @Test
    void puntoToEntityLimpiaEspaciosYConservaElRecojoPorDefecto() {
        PuntoRepartoDTO dto = new PuntoRepartoDTO();
        dto.setNombre("  Almacen Norte ");
        dto.setDireccion(" Av. Mendiola 3500 ");
        dto.setLatitud(new BigDecimal("-11.98"));
        dto.setLongitud(new BigDecimal("-77.07"));

        PuntoReparto punto = mapper.toEntity(dto);

        assertEquals("Almacen Norte", punto.getNombre());
        assertEquals("Av. Mendiola 3500", punto.getDireccion());
        assertTrue(punto.getPermiteRecojo());   // valor por defecto de la entidad
        assertTrue(punto.getActivo());
    }

    @Test
    void puntoUpdateNoPisaElEstadoCuandoElDtoNoLoTrae() {
        PuntoReparto punto = new PuntoReparto();
        punto.setActivo(false);
        PuntoRepartoDTO dto = new PuntoRepartoDTO();
        dto.setNombre("Nuevo nombre");
        dto.setDireccion("Nueva direccion");
        dto.setLatitud(BigDecimal.ONE);
        dto.setLongitud(BigDecimal.ONE);

        mapper.updateEntityFromDTO(dto, punto);

        assertEquals("Nuevo nombre", punto.getNombre());
        assertFalse(punto.getActivo());
    }

    @Test
    void tarifaUpdatePuedeLimpiarElMontoDeEnvioGratis() {
        TarifaEnvio tarifa = new TarifaEnvio();
        tarifa.setEnvioGratisDesde(new BigDecimal("1500"));
        TarifaEnvioDTO dto = new TarifaEnvioDTO();
        dto.setNombre("0 a 5 km");
        dto.setDistanciaDesdeKm(BigDecimal.ZERO);
        dto.setDistanciaHastaKm(new BigDecimal("5"));
        dto.setCosto(new BigDecimal("8"));
        dto.setEnvioGratisDesde(null);
        dto.setDiasEntregaMin(1);
        dto.setDiasEntregaMax(2);

        mapper.updateEntityFromDTO(dto, tarifa);

        assertNull(tarifa.getEnvioGratisDesde());
        assertEquals(2, tarifa.getDiasEntregaMax());
    }

    @Test
    void tarifaToDtoCopiaTodosLosCampos() {
        TarifaEnvio tarifa = new TarifaEnvio();
        tarifa.setId(3L);
        tarifa.setNombre("5 a 10 km");
        tarifa.setDistanciaDesdeKm(new BigDecimal("5"));
        tarifa.setDistanciaHastaKm(new BigDecimal("10"));
        tarifa.setCosto(new BigDecimal("12"));
        tarifa.setActivo(true);

        TarifaEnvioDTO dto = mapper.toDTO(tarifa);

        assertEquals(3L, dto.getId());
        assertEquals("5 a 10 km", dto.getNombre());
        assertEquals(new BigDecimal("12"), dto.getCosto());
        assertTrue(dto.getActivo());
    }

    @Test
    void configUpdateCopiaHoraDiasYZona() {
        ConfigEnvio config = new ConfigEnvio();
        ConfigEnvioDTO dto = new ConfigEnvioDTO();
        dto.setHoraCorte(LocalTime.of(14, 30));
        dto.setDiasPreparacion(1);
        dto.setZonaHoraria(" America/Lima ");

        mapper.updateEntityFromDTO(dto, config);

        assertEquals(LocalTime.of(14, 30), config.getHoraCorte());
        assertEquals(1, config.getDiasPreparacion());
        assertEquals("America/Lima", config.getZonaHoraria());
    }

    @Test
    void conNullDevuelveNull() {
        assertNull(mapper.toDTO((PuntoReparto) null));
        assertNull(mapper.toDTO((TarifaEnvio) null));
        assertNull(mapper.toDTO((ConfigEnvio) null));
        assertNull(mapper.toEntity((PuntoRepartoDTO) null));
        assertNull(mapper.toEntity((TarifaEnvioDTO) null));
    }
}
