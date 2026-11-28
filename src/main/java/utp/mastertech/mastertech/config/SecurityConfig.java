package utp.mastertech.mastertech.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import utp.mastertech.mastertech.exception.ErrorJson;
import utp.mastertech.mastertech.security.JwtAuthenticationFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable()) // API stateless con JWT en cabecera: no hay cookie que proteger
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/registro").permitAll()
                        // Comprobación de estado (Render, monitoreo, quien abre la raíz del API en el navegador)
                        .requestMatchers(HttpMethod.GET, "/", "/api/health").permitAll()
                        // Webhook de Stripe: sin JWT, se protege con la firma del encabezado Stripe-Signature
                        .requestMatchers(HttpMethod.POST, "/api/stripe/webhook").permitAll()
                        // Catálogo público (solo lectura)
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/categorias/**").permitAll()
                        // Cliente autenticado
                        .requestMatchers("/api/auth/me", "/api/v1/me/**", "/api/v1/pedidos/**").authenticated()
                        // Despacho: el despachador y el admin ven pedidos y cambian su estado
                        .requestMatchers("/api/v1/despacho/**").hasAnyRole("DESPACHADOR", "ADMIN")
                        // Todo lo demás de la API es solo para administradores
                        .requestMatchers("/api/v1/**").hasRole("ADMIN")
                        .anyRequest().denyAll()
                )
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                ErrorJson.escribir(req, res, HttpStatus.UNAUTHORIZED, "Debes iniciar sesión"))
                        .accessDeniedHandler((req, res, ex) ->
                                ErrorJson.escribir(req, res, HttpStatus.FORBIDDEN, "No tienes permisos para esta acción"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
