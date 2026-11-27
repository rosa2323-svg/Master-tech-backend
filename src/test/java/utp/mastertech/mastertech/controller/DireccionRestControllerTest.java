package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.dto.LugarDTO;
import utp.mastertech.mastertech.dto.SugerenciaDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.service.DireccionService;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DireccionRestControllerTest {

    private static final String TOKEN = "123e4567-e89b-12d3-a456-426614174000";

    @Mock
    private DireccionService direccionService;

    @InjectMocks
    private DireccionRestController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void disponibleReflejaLaConfiguracion() throws Exception {
        when(direccionService.habilitado()).thenReturn(true);

        String cuerpo = mvc.perform(get("/api/v1/pedidos/direcciones/disponible"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("\"disponible\":true"));
    }

    @Test
    void sugerenciasDevuelveLaLista() throws Exception {
        when(direccionService.sugerir("Av. Arequipa", TOKEN)).thenReturn(List.of(new SugerenciaDTO("ChIJ1", "Av. Arequipa 123, Lince")));

        String cuerpo = mvc.perform(get("/api/v1/pedidos/direcciones/sugerencias").param("texto", "Av. Arequipa").param("token", TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("ChIJ1"));
        assertTrue(cuerpo.contains("Av. Arequipa 123, Lince"));
    }

    @Test
    void sugerenciasSinTokenDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/pedidos/direcciones/sugerencias").param("texto", "Av. Arequipa"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(direccionService);
    }

    @Test
    void sinServicioConfiguradoDevuelve409() throws Exception {
        when(direccionService.sugerir("Av. Arequipa", TOKEN)).thenThrow(new ConflictException("El autocompletado de direcciones no está disponible."));

        mvc.perform(get("/api/v1/pedidos/direcciones/sugerencias").param("texto", "Av. Arequipa").param("token", TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void detalleDevuelveLaUbicacion() throws Exception {
        when(direccionService.detalle("ChIJ1", TOKEN)).thenReturn(new LugarDTO("Av. Arequipa 123", new BigDecimal("-12.085000"), new BigDecimal("-77.035000")));

        String cuerpo = mvc.perform(get("/api/v1/pedidos/direcciones/detalle").param("placeId", "ChIJ1").param("token", TOKEN))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("-12.085"));
    }

    @Test
    void inversaDevuelveLaDireccionDelPunto() throws Exception {
        when(direccionService.inversa(new BigDecimal("-12.091200"), new BigDecimal("-77.034500")))
                .thenReturn(new LugarDTO("Av. Arequipa 123, Lince", new BigDecimal("-12.091200"), new BigDecimal("-77.034500")));

        String cuerpo = mvc.perform(get("/api/v1/pedidos/direcciones/inversa").param("latitud", "-12.0912").param("longitud", "-77.0345"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(cuerpo.contains("Av. Arequipa 123, Lince"));
    }

    @Test
    void inversaSinParametrosDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/pedidos/direcciones/inversa")).andExpect(status().isBadRequest());
        verifyNoInteractions(direccionService);
    }
}
