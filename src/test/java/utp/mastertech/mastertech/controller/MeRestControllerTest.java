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
import utp.mastertech.mastertech.dto.VentaDTO;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.mapper.VentaMapper;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.service.UsuarioService;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeRestControllerTest {

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private VentaMapper ventaMapper;

    @InjectMocks
    private MeRestController controller;

    private MockMvc mvc;

    private final UsernamePasswordAuthenticationToken ana =
            new UsernamePasswordAuthenticationToken("ana@x.com", "n/a");

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private Usuario usuarioAna() {
        Usuario u = new Usuario();
        u.setId(4L);
        u.setNombre("Ana Pérez");
        u.setEmail("ana@x.com");
        u.setRol("USER");
        u.setDni("12345678");
        u.setDireccion("Av. Lima 123");
        u.setNumeroTelefono("987654321");
        u.setFechaRegistro(null); // evita depender del formato de fecha en la serialización
        return u;
    }

    // ---------- perfil ----------

    @Test
    void verPerfilDevuelveLosDatosDelUsuarioAutenticado() throws Exception {
        when(usuarioService.obtenerPorEmail("ana@x.com")).thenReturn(usuarioAna());

        mvc.perform(get("/api/v1/me").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.email").value("ana@x.com"))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.dni").value("12345678"))
                .andExpect(jsonPath("$.numeroTelefono").value("987654321"));
    }

    @Test
    void verPerfilNuncaExponeLaContrasena() throws Exception {
        Usuario u = usuarioAna();
        u.setPassword("$2a$hash-secreto");
        when(usuarioService.obtenerPorEmail("ana@x.com")).thenReturn(u);

        String cuerpo = mvc.perform(get("/api/v1/me").principal(ana))
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.contains("hash-secreto"));
        org.junit.jupiter.api.Assertions.assertFalse(cuerpo.toLowerCase().contains("password"));
    }

    @Test
    void actualizarPerfilRecortaElNombreYDevuelveElPerfilActualizado() throws Exception {
        when(usuarioService.obtenerPorEmail("ana@x.com")).thenReturn(usuarioAna());

        mvc.perform(put("/api/v1/me")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"  Ana Pérez  \",\"direccion\":\"Av. Lima 123\",\"numeroTelefono\":\"987654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@x.com"));

        verify(usuarioService).actualizarDatosPersonales("ana@x.com", "Ana Pérez", "Av. Lima 123", "987654321");
    }

    @Test
    void actualizarPerfilSinNombreDevuelve400() throws Exception {
        mvc.perform(put("/api/v1/me")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"numeroTelefono\":\"987654321\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).actualizarDatosPersonales(anyString(), anyString(), any(), any());
    }

    @Test
    void actualizarPerfilConTelefonoInvalidoDevuelve400() throws Exception {
        mvc.perform(put("/api/v1/me")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"numeroTelefono\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El teléfono debe tener 9 dígitos"));

        verify(usuarioService, never()).actualizarDatosPersonales(anyString(), anyString(), any(), any());
    }

    // ---------- contraseña ----------

    @Test
    void cambiarPasswordCorrectoDevuelve200() throws Exception {
        when(usuarioService.cambiarPassword("ana@x.com", "vieja123", "nueva1234", "nueva1234")).thenReturn(null);

        mvc.perform(put("/api/v1/me/password")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"vieja123\",\"passwordNuevo\":\"nueva1234\",\"passwordConfirm\":\"nueva1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada"));
    }

    @Test
    void cambiarPasswordConErrorDeNegocioDevuelve400ConElMensaje() throws Exception {
        when(usuarioService.cambiarPassword("ana@x.com", "mala", "nueva1234", "nueva1234"))
                .thenReturn("La contraseña actual es incorrecta");

        mvc.perform(put("/api/v1/me/password")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"mala\",\"passwordNuevo\":\"nueva1234\",\"passwordConfirm\":\"nueva1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("La contraseña actual es incorrecta"));
    }

    @Test
    void cambiarPasswordConCamposEnBlancoDevuelve400() throws Exception {
        mvc.perform(put("/api/v1/me/password")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwordActual\":\"\",\"passwordNuevo\":\"\",\"passwordConfirm\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioService, never()).cambiarPassword(anyString(), anyString(), anyString(), anyString());
    }

    // ---------- historial ----------

    @Test
    void historialDevuelveLasComprasDelUsuarioMapeadasADto() throws Exception {
        Usuario u = usuarioAna();
        Venta v1 = new Venta();
        v1.setId(1L);
        Venta v2 = new Venta();
        v2.setId(2L);
        when(usuarioService.obtenerPorEmail("ana@x.com")).thenReturn(u);
        when(usuarioService.obtenerHistorialCompras(u)).thenReturn(List.of(v1, v2));
        VentaDTO d1 = new VentaDTO();
        d1.setNumeroOrden("MT-0001");
        VentaDTO d2 = new VentaDTO();
        d2.setNumeroOrden("MT-0002");
        when(ventaMapper.toDTO(v1)).thenReturn(d1);
        when(ventaMapper.toDTO(v2)).thenReturn(d2);

        mvc.perform(get("/api/v1/me/pedidos").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].numeroOrden").value("MT-0001"))
                .andExpect(jsonPath("$[1].numeroOrden").value("MT-0002"));
    }

    @Test
    void historialSinComprasDevuelveListaVacia() throws Exception {
        Usuario u = usuarioAna();
        when(usuarioService.obtenerPorEmail("ana@x.com")).thenReturn(u);
        when(usuarioService.obtenerHistorialCompras(u)).thenReturn(List.of());

        mvc.perform(get("/api/v1/me/pedidos").principal(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
