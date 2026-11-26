package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.dto.HuecoTarifaDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.model.ConfigEnvio;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import utp.mastertech.mastertech.model.PuntoReparto;
import utp.mastertech.mastertech.model.TarifaEnvio;
import utp.mastertech.mastertech.service.EnvioAdminService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EnvioRestControllerTest {

    private static final String PUNTO_OK =
            "{\"nombre\":\"Almacen Norte\",\"direccion\":\"Av. Mendiola 3500\",\"latitud\":-11.98,\"longitud\":-77.07}";
    private static final String TARIFA_OK =
            "{\"nombre\":\"0 a 5 km\",\"distanciaDesdeKm\":0,\"distanciaHastaKm\":5,\"costo\":8.00,"
                    + "\"diasEntregaMin\":1,\"diasEntregaMax\":1}";

    @Mock
    private EnvioAdminService envioService;

    @Spy
    private EnvioMapper envioMapper = new EnvioMapper();

    @InjectMocks
    private EnvioRestController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private PuntoReparto punto(Long id, String nombre) {
        PuntoReparto p = new PuntoReparto();
        p.setId(id);
        p.setNombre(nombre);
        p.setDireccion("Av. Mendiola 3500");
        p.setLatitud(new BigDecimal("-11.98"));
        p.setLongitud(new BigDecimal("-77.07"));
        return p;
    }

    // ---------- puntos de reparto ----------

    @Test
    void listarPuntosDevuelveLosAlmacenes() throws Exception {
        when(envioService.listarPuntos()).thenReturn(List.of(punto(1L, "Almacen Norte")));

        String cuerpo = mvc.perform(get("/api/v1/envios/puntos-reparto"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("Almacen Norte"));
    }

    @Test
    void crearPuntoDevuelve201() throws Exception {
        mvc.perform(post("/api/v1/envios/puntos-reparto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PUNTO_OK))
                .andExpect(status().isCreated());

        verify(envioService).guardarPunto(any(PuntoReparto.class));
    }

    @Test
    void crearPuntoSinNombreDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/envios/puntos-reparto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"direccion\":\"x\",\"latitud\":-11.98,\"longitud\":-77.07}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).guardarPunto(any(PuntoReparto.class));
    }

    @Test
    void crearPuntoConLatitudFueraDeRangoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/envios/puntos-reparto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"direccion\":\"x\",\"latitud\":95,\"longitud\":-77.07}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).guardarPunto(any(PuntoReparto.class));
    }

    @Test
    void actualizarPuntoInexistenteDevuelve404() throws Exception {
        when(envioService.obtenerPunto(9L)).thenThrow(new ResourceNotFoundException("no existe"));

        mvc.perform(put("/api/v1/envios/puntos-reparto/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PUNTO_OK))
                .andExpect(status().isNotFound());
    }

    @Test
    void desactivarPuntoDevuelve200() throws Exception {
        when(envioService.cambiarActivoPunto(1L, false)).thenReturn(punto(1L, "Almacen Norte"));

        mvc.perform(patch("/api/v1/envios/puntos-reparto/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk());

        verify(envioService).cambiarActivoPunto(1L, false);
    }

    @Test
    void cambiarActivoSinElCampoDevuelve400() throws Exception {
        mvc.perform(patch("/api/v1/envios/puntos-reparto/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).cambiarActivoPunto(any(), anyBoolean());
    }

    // ---------- tarifas ----------

    @Test
    void crearTarifaDevuelve201() throws Exception {
        mvc.perform(post("/api/v1/envios/tarifas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARIFA_OK))
                .andExpect(status().isCreated());

        verify(envioService).guardarTarifa(any(TarifaEnvio.class));
    }

    @Test
    void crearTarifaConCostoNegativoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/envios/tarifas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"distanciaDesdeKm\":0,\"distanciaHastaKm\":5,\"costo\":-1,"
                                + "\"diasEntregaMin\":1,\"diasEntregaMax\":1}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).guardarTarifa(any(TarifaEnvio.class));
    }

    @Test
    void crearTarifaSuperpuestaDevuelve409() throws Exception {
        doThrow(new ConflictException("El tramo se superpone")).when(envioService).guardarTarifa(any(TarifaEnvio.class));

        mvc.perform(post("/api/v1/envios/tarifas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TARIFA_OK))
                .andExpect(status().isConflict());
    }

    @Test
    void huecosDeTarifasDevuelveLosRangosSinCobertura() throws Exception {
        when(envioService.calcularHuecos())
                .thenReturn(List.of(new HuecoTarifaDTO(new BigDecimal("5"), new BigDecimal("10"))));

        String cuerpo = mvc.perform(get("/api/v1/envios/tarifas/huecos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("desdeKm"));
    }

    // ---------- configuración ----------

    @Test
    void guardarConfiguracionValidaDevuelve200() throws Exception {
        when(envioService.obtenerConfig()).thenReturn(new ConfigEnvio());

        mvc.perform(put("/api/v1/envios/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaCorte\":\"12:00\",\"diasPreparacion\":0,\"zonaHoraria\":\"America/Lima\"}"))
                .andExpect(status().isOk());

        verify(envioService).guardarConfig(any(ConfigEnvio.class));
    }

    @Test
    void guardarConfiguracionConZonaInvalidaDevuelve400() throws Exception {
        when(envioService.obtenerConfig()).thenReturn(new ConfigEnvio());
        doThrow(new IllegalArgumentException("Zona horaria no válida")).when(envioService).guardarConfig(any(ConfigEnvio.class));

        mvc.perform(put("/api/v1/envios/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horaCorte\":\"12:00\",\"diasPreparacion\":0,\"zonaHoraria\":\"Marte/Olympus\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guardarConfiguracionSinHoraDeCorteDevuelve400() throws Exception {
        mvc.perform(put("/api/v1/envios/configuracion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diasPreparacion\":0,\"zonaHoraria\":\"America/Lima\"}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).guardarConfig(any(ConfigEnvio.class));
    }

    // ---------- feriados ----------

    @Test
    void crearFeriadoDevuelve201() throws Exception {
        DiaNoLaborable dia = new DiaNoLaborable();
        dia.setFecha(LocalDate.of(2027, 2, 1));
        dia.setDescripcion("Feriado de prueba");
        when(envioService.agregarFeriado(LocalDate.of(2027, 2, 1), "Feriado de prueba")).thenReturn(dia);

        mvc.perform(post("/api/v1/envios/feriados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"2027-02-01\",\"descripcion\":\"Feriado de prueba\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void crearFeriadoSinFechaDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/envios/feriados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"Feriado de prueba\"}"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).agregarFeriado(any(), any());
    }

    @Test
    void eliminarFeriadoDevuelve204() throws Exception {
        mvc.perform(delete("/api/v1/envios/feriados/2027-02-01"))
                .andExpect(status().isNoContent());

        verify(envioService).eliminarFeriado(LocalDate.of(2027, 2, 1));
    }

    @Test
    void eliminarFeriadoConFechaInvalidaDevuelve400() throws Exception {
        mvc.perform(delete("/api/v1/envios/feriados/no-es-una-fecha"))
                .andExpect(status().isBadRequest());

        verify(envioService, never()).eliminarFeriado(any());
    }

    @Test
    void eliminarFeriadoInexistenteDevuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("no hay feriado")).when(envioService).eliminarFeriado(LocalDate.of(2027, 2, 1));

        mvc.perform(delete("/api/v1/envios/feriados/2027-02-01"))
                .andExpect(status().isNotFound());
    }
}
