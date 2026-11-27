package utp.mastertech.mastertech.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import utp.mastertech.mastertech.model.PuntoReparto;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DistanciaRutaServiceTest {

    private DistanciaRutaService service;
    private final BigDecimal lat = new BigDecimal("-12.085000");
    private final BigDecimal lng = new BigDecimal("-77.035000");

    private PuntoReparto sede() {
        PuntoReparto p = new PuntoReparto();
        p.setId(3L);
        p.setLatitud(new BigDecimal("-12.170000"));
        p.setLongitud(new BigDecimal("-77.010000"));
        return p;
    }

    @BeforeEach
    void preparar() {
        service = spy(new DistanciaRutaService());
        ReflectionTestUtils.setField(service, "apiKey", "clave-de-prueba");
    }

    @Test
    void sinClaveNoLlamaAGoogle() throws Exception {
        ReflectionTestUtils.setField(service, "apiKey", "");

        assertTrue(service.distanciaPorRutaKm(sede(), lat, lng).isEmpty());
        assertFalse(service.habilitado());
        verify(service, never()).solicitar(anyString());
    }

    @Test
    void convierteLosMetrosDeGoogleAKilometros() throws Exception {
        doReturn("{\"routes\":[{\"distanceMeters\":8421}]}").when(service).solicitar(anyString());

        Optional<BigDecimal> km = service.distanciaPorRutaKm(sede(), lat, lng);

        assertEquals(0, new BigDecimal("8.42").compareTo(km.orElseThrow()));
    }

    @Test
    void repiteLaMismaConsultaDesdeLaCache() throws Exception {
        doReturn("{\"routes\":[{\"distanceMeters\":5000}]}").when(service).solicitar(anyString());

        service.distanciaPorRutaKm(sede(), lat, lng);
        service.distanciaPorRutaKm(sede(), lat, lng);

        verify(service, times(1)).solicitar(anyString());
    }

    @Test
    void siGoogleFallaDevuelveVacioSinLanzarExcepcion() throws Exception {
        doThrow(new IllegalStateException("Google respondió HTTP 403")).when(service).solicitar(anyString());

        assertTrue(service.distanciaPorRutaKm(sede(), lat, lng).isEmpty());
    }

    @Test
    void unaRespuestaSinRutasEsVacia() {
        assertTrue(DistanciaRutaService.extraerKm("{}").isEmpty());
        assertTrue(DistanciaRutaService.extraerKm(null).isEmpty());
    }

    @Test
    void unaRutaSinDistanciaCuentaComoCeroKm() {
        assertEquals(0, BigDecimal.ZERO.compareTo(DistanciaRutaService.extraerKm("{\"routes\":[{}]}").orElseThrow()));
    }

    @Test
    void elCuerpoUsaPuntoDecimalYModoAuto() {
        String cuerpo = DistanciaRutaService.cuerpo(sede(), lat, lng);

        assertTrue(cuerpo.contains("\"latitude\":-12.170000"));
        assertTrue(cuerpo.contains("\"longitude\":-77.035000"));
        assertTrue(cuerpo.contains("\"travelMode\":\"DRIVE\""));
    }
}
