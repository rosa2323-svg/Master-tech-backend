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
import utp.mastertech.mastertech.mapper.VentaMapper;
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
    void listarSinPedidosDevuelveListaVacia() throws Exception {
        when(ventaService.listarParaDespacho()).thenReturn(List.of());

        mvc.perform(get("/api/v1/despacho/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cambiarEstadoDevuelveElPedidoActualizado() throws Exception {
        Venta venta = new Venta();
        when(ventaService.cambiarEstado(1L, "Preparando")).thenReturn(venta);
        when(ventaMapper.toDTO(venta)).thenReturn(dto(1L, "Preparando"));

        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Preparando\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.estado").value("Preparando"));

        verify(ventaService).cambiarEstado(1L, "Preparando");
    }

    @Test
    void cambiarEstadoDePedidoInexistenteDevuelve404() throws Exception {
        when(ventaService.cambiarEstado(99L, "Preparando"))
                .thenThrow(new ResourceNotFoundException("Venta no encontrada: 99"));

        mvc.perform(patch("/api/v1/despacho/pedidos/99/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"Preparando\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Venta no encontrada: 99"));
    }

    @Test
    void cambiarEstadoConTransicionInvalidaDevuelve400ConElMotivo() throws Exception {
        when(ventaService.cambiarEstado(1L, "Entregado"))
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
