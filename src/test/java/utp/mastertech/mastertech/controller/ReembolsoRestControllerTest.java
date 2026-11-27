package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.dto.SolicitudReembolsoDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.exception.ConflictException;
import utp.mastertech.mastertech.mapper.ReembolsoMapper;
import utp.mastertech.mastertech.model.SolicitudReembolso;
import utp.mastertech.mastertech.service.ReembolsoService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReembolsoRestControllerTest {

    @Mock
    private ReembolsoService reembolsoService;
    @Mock
    private ReembolsoMapper reembolsoMapper;

    @InjectMocks
    private ReembolsoRestController controller;

    private MockMvc mvc;

    private final UsernamePasswordAuthenticationToken admin =
            new UsernamePasswordAuthenticationToken("admin@x.com", "n/a");

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private SolicitudReembolsoDTO dto(String estado) {
        SolicitudReembolsoDTO d = new SolicitudReembolsoDTO();
        d.setId(3L);
        d.setEstado(estado);
        return d;
    }

    @Test
    void listarFiltraPorEstado() throws Exception {
        SolicitudReembolso s = new SolicitudReembolso();
        when(reembolsoService.listar("PENDIENTE")).thenReturn(List.of(s));
        when(reembolsoMapper.toDTO(s)).thenReturn(dto("PENDIENTE"));

        mvc.perform(get("/api/v1/reembolsos").param("estado", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    void aprobarDevuelveLaSolicitudResuelta() throws Exception {
        SolicitudReembolso s = new SolicitudReembolso();
        when(reembolsoService.aprobar(3L, "admin@x.com", "ok")).thenReturn(s);
        when(reembolsoMapper.toDTO(s)).thenReturn(dto("APROBADA"));

        mvc.perform(post("/api/v1/reembolsos/3/aprobar").principal(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
    }

    @Test
    void aprobarSinCuerpoFunciona() throws Exception {
        SolicitudReembolso s = new SolicitudReembolso();
        when(reembolsoService.aprobar(3L, "admin@x.com", null)).thenReturn(s);
        when(reembolsoMapper.toDTO(s)).thenReturn(dto("APROBADA"));

        mvc.perform(post("/api/v1/reembolsos/3/aprobar").principal(admin))
                .andExpect(status().isOk());
    }

    @Test
    void aprobarUnaSolicitudResueltaDevuelve409() throws Exception {
        when(reembolsoService.aprobar(eq(3L), any(), any())).thenThrow(new ConflictException("Esta solicitud ya fue resuelta."));

        mvc.perform(post("/api/v1/reembolsos/3/aprobar").principal(admin))
                .andExpect(status().isConflict());
    }

    @Test
    void rechazarSinNotaDevuelve400() throws Exception {
        when(reembolsoService.rechazar(eq(3L), any(), any()))
                .thenThrow(new IllegalArgumentException("Explica al cliente por qué se rechaza el reembolso."));

        mvc.perform(post("/api/v1/reembolsos/3/rechazar").principal(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rechazarConNotaDevuelveLaSolicitud() throws Exception {
        SolicitudReembolso s = new SolicitudReembolso();
        when(reembolsoService.rechazar(3L, "admin@x.com", "no aplica")).thenReturn(s);
        when(reembolsoMapper.toDTO(s)).thenReturn(dto("RECHAZADA"));

        mvc.perform(post("/api/v1/reembolsos/3/rechazar").principal(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nota\":\"no aplica\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }
}
