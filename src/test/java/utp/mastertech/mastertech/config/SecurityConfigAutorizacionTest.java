package utp.mastertech.mastertech.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import utp.mastertech.mastertech.security.JwtAuthenticationFilter;
import utp.mastertech.mastertech.security.JwtUtil;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba las reglas de acceso de SecurityConfig con el filtro JWT REAL y tokens reales (no standalone):
 * quién puede entrar a cada grupo de rutas y qué formato tienen los 401/403.
 * Los endpoints son de mentira: aquí solo importa si la seguridad deja pasar o no.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = SecurityConfigAutorizacionTest.Config.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class SecurityConfigAutorizacionTest {

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean
        JwtUtil jwtUtil() {
            return new JwtUtil("clave-secreta-solo-para-pruebas-de-autorizacion-0123456789", 3_600_000L);
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter();
        }

        @Bean
        Endpoints endpoints() {
            return new Endpoints();
        }
    }

    @RestController
    static class Endpoints {
        @GetMapping("/") String raiz() { return "{}"; }
        @GetMapping("/api/health") String salud() { return "{}"; }
        @GetMapping("/api/v1/productos") String productos() { return "[]"; }
        @GetMapping("/api/v1/categorias") String categorias() { return "[]"; }
        @PostMapping("/api/auth/login") String login() { return "{}"; }
        @PostMapping("/api/stripe/webhook") String webhook() { return "{}"; }
        @GetMapping("/api/auth/me") String me() { return "{}"; }
        @GetMapping("/api/v1/me/pedidos") String misPedidos() { return "[]"; }
        @PostMapping("/api/v1/pedidos/checkout") String checkout() { return "{}"; }
        @GetMapping("/api/v1/despacho/pedidos") String despacho() { return "[]"; }
        @PatchMapping("/api/v1/despacho/pedidos/1/estado") String cambiarEstado() { return "{}"; }
        @GetMapping("/api/v1/reembolsos") String reembolsos() { return "[]"; }
        @GetMapping("/api/v1/ventas") String ventas() { return "[]"; }
        @GetMapping("/api/v1/usuarios") String usuarios() { return "[]"; }
        @GetMapping("/api/v1/envios/tarifas") String tarifas() { return "[]"; }
    }

    @Autowired
    private WebApplicationContext contexto;
    @Autowired
    private JwtUtil jwtUtil;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private String bearer(String rol) {
        return "Bearer " + jwtUtil.generarToken(rol.toLowerCase() + "@x.com", rol);
    }

    // ---------- rutas públicas ----------

    @Test
    void rutasPublicasNoPidenToken() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/productos")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
    }

    @Test
    void elWebhookDeStripeNoPideTokenPeroSoloAceptaPost() throws Exception {
        mvc.perform(post("/api/stripe/webhook").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(get("/api/stripe/webhook")).andExpect(status().isUnauthorized());
    }

    // ---------- sin sesión ----------

    @Test
    void sinTokenLasRutasPrivadasDan401ConElFormatoDeError() throws Exception {
        mvc.perform(get("/api/v1/me/pedidos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Debes iniciar sesión"))
                .andExpect(jsonPath("$.path").value("/api/v1/me/pedidos"));
        mvc.perform(get("/api/v1/despacho/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/reembolsos")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/pedidos/checkout").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenInvalidoDa401() throws Exception {
        mvc.perform(get("/api/v1/me/pedidos").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---------- cliente (USER) ----------

    @Test
    void unClienteUsaSusRutasPeroNoLasDeDespachoNiAdmin() throws Exception {
        String user = bearer("USER");
        mvc.perform(get("/api/auth/me").header("Authorization", user)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/pedidos").header("Authorization", user)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/pedidos/checkout").header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/despacho/pedidos").header("Authorization", user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tienes permisos para esta acción"));
        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado").header("Authorization", user)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reembolsos").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ventas").header("Authorization", user)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", user)).andExpect(status().isForbidden());
    }

    // ---------- despachador ----------

    @Test
    void elDespachadorVeDespachoPeroNoReembolsosNiAdministracion() throws Exception {
        String desp = bearer("DESPACHADOR");
        mvc.perform(get("/api/v1/despacho/pedidos").header("Authorization", desp)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/despacho/pedidos/1/estado").header("Authorization", desp)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        mvc.perform(get("/api/v1/reembolsos").header("Authorization", desp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ventas").header("Authorization", desp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", desp)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/envios/tarifas").header("Authorization", desp)).andExpect(status().isForbidden());
    }

    // ---------- admin ----------

    @Test
    void elAdminAccedeATodo() throws Exception {
        String admin = bearer("ADMIN");
        mvc.perform(get("/api/v1/despacho/pedidos").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reembolsos").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/ventas").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/usuarios").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/envios/tarifas").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/pedidos").header("Authorization", admin)).andExpect(status().isOk());
    }
}
