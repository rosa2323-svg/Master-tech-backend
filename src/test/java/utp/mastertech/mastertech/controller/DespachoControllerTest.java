package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.mapper.EnvioMapper;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.dto.EnvioEventoDTO;
import utp.mastertech.mastertech.model.EnvioEvento;
import java.time.LocalDateTime;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.service.VentaService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DespachoControllerTest {

    @Mock
    private VentaService ventaService;
    @Mock
    private VentaMapper ventaMapper;
    @Mock
    private EnvioMapper envioMapper;

    @InjectMocks
    private DespachoController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private VentaDTO dto(Long id, String estado) {
        VentaDTO d = new VentaDTO();
        d.setId(id);
        d.setEstado(estado);
        return d;
    }

    @Test
    void listarDevuelveLosPedidosDelFlujoDeDespacho() throws Exception {
        Venta v1 = new Venta();
        v1.setId(2L);
        Venta v2 = new Venta();
        v2.setId(1L);
        when(ventaService.listarParaDespacho()).thenReturn(List.of(v1, v2));
        when(ventaMapper.toDTO(v1)).thenReturn(dto(2L, "Pagado"));
        when(ventaMapper.toDTO(v2)).thenReturn(dto(1L, "Preparando"));

        mvc.perform(get("/api/v1/despacho/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[1].estado").value("Preparando"));
    }

    @Test
    void listarMarcaLosPedidosConReembolsoPendiente() throws Exception {
        Venta v1 = new Venta();
        v1.setId(2L);
        Venta v2 = new Venta();
        v2.setId(1L);
        when(ventaService.listarParaDespacho()).thenReturn(List.of(v1, v2));
        when(ventaService.ventasConReembolsoPendiente()).thenReturn(java.util.Set.of(2L));
        when(ventaMapper.toDTO(v1)).thenReturn(dto(2L, "Pagado"));
        when(ventaMapper.toDTO(v2)).thenReturn(dto(1L, "Preparando"));

        mvc.perform(get("/api/v1/despacho/pedidos"))
                .andExpect(jsonPath("$[0].reembolsoPendiente").value(true))
                .andExpect(jsonPath("$[1].reembolsoPendiente").value(false));
    }

    @Test
    void listarSinPedidosDevuelveListaVacia() throws Exception {
        when(ventaService.listarParaDespacho()).thenReturn(List.of());

        mvc.perform(get("/api/v1/despacho/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cambiarEstadoDevuelveElPedidoActualizado() throws Exception {
        Venta venta = new Venta();
        when(ventaService.cambiarEstado(eq(1L), eq("Preparando"), isNull(), isNull())).thenReturn(venta);
        when(ventaMapper.toDTO(venta)).thenReturn(dto(1L, "Preparando"));

        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Preparando\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("Preparando"));

        verify(ventaService).cambiarEstado(1L, "Preparando", null, null);
    }

    @Test
    void cambiarEstadoEnviaLaNotaAlServicio() throws Exception {
        Venta venta = new Venta();
        when(ventaService.cambiarEstado(1L, "Preparando", "Embalado", null)).thenReturn(venta);
        when(ventaMapper.toDTO(venta)).thenReturn(dto(1L, "Preparando"));

        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Preparando\",\"nota\":\"Embalado\"}"))
                .andExpect(status().isOk());

        verify(ventaService).cambiarEstado(1L, "Preparando", "Embalado", null);
    }

    @Test
    void cambiarEstadoSinEstadoDevuelve400() throws Exception {
        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":\"hola\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ventaService);
    }

    @Test
    void eventosDevuelveElHistorialDelPedido() throws Exception {
        EnvioEvento e = new EnvioEvento();
        when(ventaService.listarEventos(1L)).thenReturn(List.of(e));
        when(envioMapper.toDTO(e)).thenReturn(new EnvioEventoDTO("Pagado", LocalDateTime.of(2026, 11, 26, 9, 0), null, "Pago confirmado"));

        mvc.perform(get("/api/v1/despacho/pedidos/1/eventos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("Pagado"))
                .andExpect(jsonPath("$[0].nota").value("Pago confirmado"));
    }

    @Test
    void eventosDePedidoInexistenteDevuelve404() throws Exception {
        when(ventaService.listarEventos(99L)).thenThrow(new ResourceNotFoundException("Venta no encontrada: 99"));

        mvc.perform(get("/api/v1/despacho/pedidos/99/eventos"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarEstadoDePedidoInexistenteDevuelve404() throws Exception {
        when(ventaService.cambiarEstado(eq(99L), eq("Preparando"), isNull(), isNull()))
                .thenThrow(new ResourceNotFoundException("Venta no encontrada: 99"));

        mvc.perform(patch("/api/v1/despacho/pedidos/99/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Preparando\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Venta no encontrada: 99"));
    }

    @Test
    void cambiarEstadoConTransicionInvalidaDevuelve400ConElMotivo() throws Exception {
        when(ventaService.cambiarEstado(eq(1L), eq("Entregado"), isNull(), isNull()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Desde Pagado solo se puede pasar a Preparando"));

        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Entregado\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Desde Pagado solo se puede pasar a Preparando"));
    }

    @Test
    void cambiarEstadoConCuerpoInvalidoDevuelve400() throws Exception {
        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("esto no es json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ventaService);
    }
}
