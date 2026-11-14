package utp.mastertech.mastertech.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.mapper.UsuarioMapper;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UsuarioRestControllerTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Spy
    private UsuarioMapper usuarioMapper = new UsuarioMapper();

    @InjectMocks
    private UsuarioRestController controller;

    private MockMvc mvc;

    private final UsernamePasswordAuthenticationToken admin =
            new UsernamePasswordAuthenticationToken("admin@x.com", "n/a");

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private Usuario usuario(Long id, String email, String rol) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setNombre("Usuario " + id);
        u.setEmail(email);
        u.setPassword("HASH");
        u.setRol(rol);
        u.setFechaRegistro(null); // evita depender del formato de fecha en la serialización
        return u;
    }

    private static String jsonNuevo(String rol, String password) {
        return "{\"nombre\":\"Luis Soto\",\"email\":\"luis@x.com\""
                + (password != null ? ",\"password\":\"" + password + "\"" : "")
                + (rol != null ? ",\"rol\":\"" + rol + "\"" : "")
                + "}";
    }

    // ---------- lectura ----------

    @Test
    void listarDevuelveLosUsuariosSinExponerContrasenas() throws Exception {
        when(usuarioRepository.findAll()).thenReturn(List.of(
                usuario(1L, "admin@x.com", "ADMIN"), usuario(2L, "ana@x.com", "USER")));

        String cuerpo = mvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].email").value("admin@x.com"))
                .andExpect(jsonPath("$[1].rol").value("USER"))
                .andReturn().getResponse().getContentAsString();

        assertFalse(cuerpo.contains("HASH"));
    }

    @Test
    void obtenerPorIdExistenteDevuelve200() throws Exception {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, "ana@x.com", "USER")));

        mvc.perform(get("/api/v1/usuarios/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Usuario 2"));
    }

    @Test
    void obtenerPorIdInexistenteDevuelve404ConMensaje() throws Exception {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/usuarios/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Usuario no encontrado: 99"));
    }

    // ---------- crear ----------

    @Test
    void crearEncriptaLaContrasenaYDevuelve201() throws Exception {
        when(passwordEncoder.encode("secreto123")).thenReturn("HASH-NUEVO");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo("ADMIN", "secreto123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("luis@x.com"))
                .andExpect(jsonPath("$.rol").value("ADMIN"));

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("HASH-NUEVO", captor.getValue().getPassword());
        assertNull(captor.getValue().getId());
    }

    @Test
    void crearSinRolAsignaUSERPorDefecto() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("HASH-NUEVO");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo(null, "secreto123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("USER"));
    }

    @Test
    void crearAceptaElRolDespachador() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("HASH-NUEVO");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo("DESPACHADOR", "secreto123")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("DESPACHADOR"));
    }

    @Test
    void crearSinContrasenaDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo("USER", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña es obligatoria"));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearConContrasenaCortaDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo("USER", "corta")))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearConRolInvalidoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonNuevo("SUPERUSUARIO", "secreto123")))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void crearConEmailInvalidoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Luis Soto\",\"email\":\"no-es-correo\",\"password\":\"secreto123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- actualizar (PUT) ----------

    @Test
    void actualizarCambiaLosDatosYConservaLaContrasenaSiNoSeEnvia() throws Exception {
        Usuario existente = usuario(2L, "ana@x.com", "USER");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(put("/api/v1/usuarios/2")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Nueva\",\"email\":\"ana@x.com\",\"rol\":\"DESPACHADOR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Nueva"))
                .andExpect(jsonPath("$.rol").value("DESPACHADOR"));

        assertEquals("HASH", existente.getPassword());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void actualizarConContrasenaNuevaLaEncripta() throws Exception {
        Usuario existente = usuario(2L, "ana@x.com", "USER");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(existente));
        when(passwordEncoder.encode("otra-clave-1")).thenReturn("HASH-2");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(put("/api/v1/usuarios/2")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"password\":\"otra-clave-1\"}"))
                .andExpect(status().isOk());

        assertEquals("HASH-2", existente.getPassword());
    }

    @Test
    void actualizarUsuarioInexistenteDevuelve404() throws Exception {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/usuarios/99")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void actualizarConRolInvalidoDevuelve400() throws Exception {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, "ana@x.com", "USER")));

        mvc.perform(put("/api/v1/usuarios/2")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"ROOT\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdminNoPuedeQuitarseElRolAdministradorConPut() throws Exception {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin@x.com", "ADMIN")));

        mvc.perform(put("/api/v1/usuarios/1")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Admin\",\"email\":\"admin@x.com\",\"rol\":\"USER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puedes quitarte a ti mismo el rol de administrador."));

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unAdminPuedeMantenerseComoAdminEnSuPropioPut() throws Exception {
        Usuario yo = usuario(1L, "ADMIN@x.com", "ADMIN"); // el email se compara sin distinguir mayúsculas
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(yo));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(put("/api/v1/usuarios/1")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Admin\",\"email\":\"admin@x.com\",\"rol\":\"ADMIN\"}"))
                .andExpect(status().isOk());
    }

    // ---------- actualizar parcial (PATCH) ----------

    @Test
    void patchCambiaSoloLosCamposEnviados() throws Exception {
        Usuario existente = usuario(2L, "ana@x.com", "USER");
        existente.setDireccion("Vieja 1");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(existente));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        mvc.perform(patch("/api/v1/usuarios/2")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ADMIN\",\"nombre\":\"Ana Admin\",\"numeroTelefono\":\"987654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"))
                .andExpect(jsonPath("$.nombre").value("Ana Admin"));

        assertEquals("Vieja 1", existente.getDireccion());
        assertEquals("987654321", existente.getNumeroTelefono());
    }

    @Test
    void patchConRolInvalidoDevuelve400() throws Exception {
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(usuario(2L, "ana@x.com", "USER")));

        mvc.perform(patch("/api/v1/usuarios/2")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"ROOT\"}"))
                .andExpect(status().isBadRequest());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void patchNoPermiteQuitarseElRolAdministradorAUnoMismo() throws Exception {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "admin@x.com", "ADMIN")));

        mvc.perform(patch("/api/v1/usuarios/1")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"DESPACHADOR\"}"))
                .andExpect(status().isConflict());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void patchDeUsuarioInexistenteDevuelve404() throws Exception {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(patch("/api/v1/usuarios/99")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------- eliminar ----------

    @Test
    void eliminarOtroUsuarioDevuelve204() throws Exception {
        Usuario ana = usuario(2L, "ana@x.com", "USER");
        when(usuarioRepository.findById(2L)).thenReturn(Optional.of(ana));

        mvc.perform(delete("/api/v1/usuarios/2").principal(admin))
                .andExpect(status().isNoContent());

        verify(usuarioRepository).delete(ana);
    }

    @Test
    void eliminarTuPropiaCuentaDevuelve409() throws Exception {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario(1L, "Admin@X.com", "ADMIN")));

        mvc.perform(delete("/api/v1/usuarios/1").principal(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puedes eliminar tu propia cuenta."));

        verify(usuarioRepository, never()).delete(any(Usuario.class));
    }

    @Test
    void eliminarUsuarioInexistenteDevuelve404() throws Exception {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        mvc.perform(delete("/api/v1/usuarios/99").principal(admin))
                .andExpect(status().isNotFound());
    }
}
