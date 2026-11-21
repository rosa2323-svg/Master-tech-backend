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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import utp.mastertech.mastertech.exception.ApiExceptionHandler;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.exception.StorageException;
import utp.mastertech.mastertech.mapper.ProductoMapper;
import utp.mastertech.mastertech.model.Categoria;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.service.ProductoService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductoRestControllerTest {

    @Mock
    private ProductoService productoService;

    @Spy
    private ProductoMapper productoMapper = new ProductoMapper();

    @InjectMocks
    private ProductoRestController controller;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private Categoria categoria(Long id, String nombre) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        return c;
    }

    private Producto producto(Long id, String nombre, Categoria categoria) {
        Producto p = new Producto();
        p.setId(id);
        p.setNombre(nombre);
        p.setPrecio(100.0);
        p.setStock(5);
        p.setCategoria(categoria);
        return p;
    }

    private static final String JSON_VALIDO =
            "{\"nombre\":\"Laptop X\",\"precio\":2500.5,\"stock\":3,\"categoriaId\":1}";

    // ---------- lectura ----------

    @Test
    void listarDevuelveLosProductosComoDto() throws Exception {
        when(productoService.listarProductosActivos()).thenReturn(List.of(
                producto(1L, "Laptop X", categoria(1L, "Laptops")),
                producto(2L, "Mouse Y", categoria(2L, "Accesorios"))));

        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("Laptop X"))
                .andExpect(jsonPath("$[0].categoriaNombre").value("Laptops"))
                .andExpect(jsonPath("$[1].categoriaId").value(2));
    }

    @Test
    void obtenerPorIdExistenteDevuelve200() throws Exception {
        when(productoService.obtenerProducto(1L))
                .thenReturn(Optional.of(producto(1L, "Laptop X", categoria(1L, "Laptops"))));

        mvc.perform(get("/api/v1/productos/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void obtenerPorIdInexistenteDevuelve404() throws Exception {
        when(productoService.obtenerProducto(99L)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/productos/99"))
                .andExpect(status().isNotFound());
    }


    private static org.springframework.security.core.Authentication autenticado(String rol) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "x@mail.com", null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority(rol)));
    }

    @Test
    void listarPublicoNuncaIncluyeInhabilitadosAunqueSePidanTodos() throws Exception {
        when(productoService.listarProductosActivos()).thenReturn(List.of(
                producto(1L, "Laptop X", categoria(1L, "Laptops"))));

        mvc.perform(get("/api/v1/productos").param("todos", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(productoService, never()).listarProductos();
    }

    @Test
    void listarConTodosYRolUserSigueDevolviendoSoloActivos() throws Exception {
        when(productoService.listarProductosActivos()).thenReturn(List.of());

        mvc.perform(get("/api/v1/productos").param("todos", "true").principal(autenticado("ROLE_USER")))
                .andExpect(status().isOk());

        verify(productoService, never()).listarProductos();
    }

    @Test
    void adminConTodosVeTambienLosInhabilitados() throws Exception {
        Producto off = producto(2L, "Mouse viejo", categoria(2L, "Accesorios"));
        off.setActivo(false);
        when(productoService.listarProductos()).thenReturn(List.of(
                producto(1L, "Laptop X", categoria(1L, "Laptops")), off));

        mvc.perform(get("/api/v1/productos").param("todos", "true").principal(autenticado("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[1].activo").value(false));

        verify(productoService, never()).listarProductosActivos();
    }

    @Test
    void productoInhabilitadoDaNotFoundAlPublicoYSeVeComoAdmin() throws Exception {
        Producto off = producto(2L, "Mouse viejo", categoria(2L, "Accesorios"));
        off.setActivo(false);
        when(productoService.obtenerProducto(2L)).thenReturn(Optional.of(off));

        mvc.perform(get("/api/v1/productos/2")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/productos/2").principal(autenticado("ROLE_USER"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/productos/2").principal(autenticado("ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    @Test
    void crearSinActivoQuedaHabilitado() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isCreated());

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoService).guardarProducto(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getActivo());
    }

    // ---------- crear ----------

    @Test
    void crearDevuelve201ConElProductoGuardado() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Laptop X"));

        verify(productoService).guardarProducto(any(Producto.class));
    }

    @Test
    void crearIgnoraElIdEnviadoPorElCliente() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":55,\"nombre\":\"Laptop X\",\"precio\":10,\"stock\":1,\"categoriaId\":1}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<Producto> captor = ArgumentCaptor.forClass(Producto.class);
        verify(productoService).guardarProducto(captor.capture());
        assertNull(captor.getValue().getId());
    }

    @Test
    void crearDevuelveElProductoRecargadoDesdeElServicio() throws Exception {
        Producto recargado = producto(7L, "Laptop X (BD)", categoria(1L, "Laptops"));
        when(productoService.obtenerProducto(any())).thenReturn(Optional.of(recargado));

        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.categoriaNombre").value("Laptops"));
    }

    @Test
    void crearSinNombreDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":10,\"stock\":1,\"categoriaId\":1}"))
                .andExpect(status().isBadRequest());

        verify(productoService, never()).guardarProducto(any(Producto.class));
    }

    @Test
    void crearConPrecioNegativoOStockNegativoDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"A\",\"precio\":-1,\"stock\":1,\"categoriaId\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"A\",\"precio\":1,\"stock\":-4,\"categoriaId\":1}"))
                .andExpect(status().isBadRequest());

        verify(productoService, never()).guardarProducto(any(Producto.class));
    }

    @Test
    void crearSinCategoriaDevuelve400() throws Exception {
        mvc.perform(post("/api/v1/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"A\",\"precio\":1,\"stock\":1}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- actualizar ----------

    @Test
    void actualizarExistenteAplicaLosCambiosYDevuelve200() throws Exception {
        Producto existente = producto(1L, "Viejo", categoria(1L, "Laptops"));
        when(productoService.obtenerProducto(1L)).thenReturn(Optional.of(existente));

        mvc.perform(put("/api/v1/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Laptop X"))
                .andExpect(jsonPath("$.precio").value(2500.5));

        verify(productoService).guardarProducto(existente);
        assertEquals("Laptop X", existente.getNombre());
        assertEquals(3, existente.getStock());
    }

    @Test
    void actualizarInexistenteDevuelve404() throws Exception {
        when(productoService.obtenerProducto(99L)).thenReturn(Optional.empty());

        mvc.perform(put("/api/v1/productos/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON_VALIDO))
                .andExpect(status().isNotFound());

        verify(productoService, never()).guardarProducto(any(Producto.class));
    }

    @Test
    void actualizarConDatosInvalidosDevuelve400() throws Exception {
        mvc.perform(put("/api/v1/productos/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":1,\"stock\":1,\"categoriaId\":1}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- imagen ----------

    @Test
    void subirImagenGuardaElProductoConLaImagen() throws Exception {
        Producto existente = producto(1L, "Laptop X", categoria(1L, "Laptops"));
        when(productoService.obtenerProducto(1L)).thenReturn(Optional.of(existente));
        MockMultipartFile imagen = new MockMultipartFile("imagen", "foto.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/productos/1/imagen").file(imagen))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(productoService).guardarProducto(eq(existente), any());
    }

    @Test
    void subirImagenDeProductoInexistenteDevuelve404() throws Exception {
        when(productoService.obtenerProducto(99L)).thenReturn(Optional.empty());
        MockMultipartFile imagen = new MockMultipartFile("imagen", "foto.png", "image/png", new byte[]{1});

        mvc.perform(multipart("/api/v1/productos/99/imagen").file(imagen))
                .andExpect(status().isNotFound());

        verify(productoService, never()).guardarProducto(any(Producto.class), any());
    }

    @Test
    void subirImagenSinArchivoDevuelve400() throws Exception {
        mvc.perform(multipart("/api/v1/productos/1/imagen"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void subirImagenConFalloDeAlmacenamientoDevuelve502() throws Exception {
        Producto existente = producto(1L, "Laptop X", categoria(1L, "Laptops"));
        when(productoService.obtenerProducto(1L)).thenReturn(Optional.of(existente));
        doThrow(new StorageException("No se pudo subir la imagen. Inténtalo de nuevo."))
                .when(productoService).guardarProducto(eq(existente), any());
        MockMultipartFile imagen = new MockMultipartFile("imagen", "foto.png", "image/png", new byte[]{1});

        mvc.perform(multipart("/api/v1/productos/1/imagen").file(imagen))
                .andExpect(status().isBadGateway());
    }

    // ---------- habilitar / inhabilitar ----------

    @Test
    void inhabilitarDevuelve200ConElProductoInactivo() throws Exception {
        Producto off = producto(1L, "Laptop X", categoria(1L, "Laptops"));
        off.setActivo(false);
        when(productoService.cambiarActivo(1L, false)).thenReturn(off);

        mvc.perform(patch("/api/v1/productos/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.activo").value(false));

        verify(productoService).cambiarActivo(1L, false);
    }

    @Test
    void habilitarDevuelve200ConElProductoActivo() throws Exception {
        when(productoService.cambiarActivo(1L, true))
                .thenReturn(producto(1L, "Laptop X", categoria(1L, "Laptops")));

        mvc.perform(patch("/api/v1/productos/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void inhabilitarInexistenteDevuelve404() throws Exception {
        when(productoService.cambiarActivo(99L, false))
                .thenThrow(new ResourceNotFoundException("Producto no encontrado: 99"));

        mvc.perform(patch("/api/v1/productos/99/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cambiarActivoSinElCampoDevuelve400() throws Exception {
        mvc.perform(patch("/api/v1/productos/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoService);
    }

    @Test
    void yaNoExisteElBorradoDeProductos() throws Exception {
        mvc.perform(delete("/api/v1/productos/1"))
                .andExpect(status().isMethodNotAllowed());
    }

    // ---------- keywords (autocompletado) ----------

    @Test
    void keywordsBuscaEnNombreDeProductoYEnCategoriaSinDistinguirMayusculas() throws Exception {
        when(productoService.listarProductosActivos()).thenReturn(List.of(
                producto(1L, "Laptop Dell", categoria(1L, "Computo")),
                producto(2L, "Mouse Gamer", categoria(2L, "Laptop accesorios")),
                producto(3L, "Teclado", categoria(3L, "Perifericos"))));
        when(productoService.listarCategorias()).thenReturn(List.of(
                categoria(1L, "Laptops"), categoria(3L, "Perifericos")));

        mvc.perform(get("/api/v1/productos/keywords").param("term", "LAPTOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("Laptop Dell"))
                .andExpect(jsonPath("$[1]").value("Mouse Gamer"))
                .andExpect(jsonPath("$[2]").value("Laptops"));
    }

    @Test
    void keywordsLimitaA4ProductosYA2CategoriasSinRepetidos() throws Exception {
        Categoria c = categoria(1L, "Laptops");
        when(productoService.listarProductosActivos()).thenReturn(List.of(
                producto(1L, "Laptop A", c),
                producto(2L, "Laptop A", c),   // repetido: se descarta
                producto(3L, "Laptop B", c),
                producto(4L, "Laptop C", c),
                producto(5L, "Laptop D", c),
                producto(6L, "Laptop E", c)));
        when(productoService.listarCategorias()).thenReturn(List.of(
                categoria(1L, "Laptops"), categoria(2L, "Laptop gamer"), categoria(3L, "Laptop oficina")));

        mvc.perform(get("/api/v1/productos/keywords").param("term", "laptop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0]").value("Laptop A"))
                .andExpect(jsonPath("$[3]").value("Laptop D"))
                .andExpect(jsonPath("$[4]").value("Laptops"))
                .andExpect(jsonPath("$[5]").value("Laptop gamer"));
    }

    @Test
    void keywordsToleraProductosSinNombreOSinCategoria() throws Exception {
        Producto sinNombre = producto(1L, null, categoria(1L, "Perifericos"));
        Producto sinCategoria = producto(2L, "Audifonos", null);
        when(productoService.listarProductosActivos()).thenReturn(List.of(sinNombre, sinCategoria));
        when(productoService.listarCategorias()).thenReturn(List.of());

        mvc.perform(get("/api/v1/productos/keywords").param("term", "audi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("Audifonos"));
    }

    @Test
    void keywordsSinCoincidenciasDevuelveListaVacia() throws Exception {
        when(productoService.listarProductosActivos()).thenReturn(List.of(
                producto(1L, "Laptop Dell", categoria(1L, "Computo"))));
        when(productoService.listarCategorias()).thenReturn(List.of(categoria(1L, "Computo")));

        mvc.perform(get("/api/v1/productos/keywords").param("term", "zzz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void keywordsSinElParametroTermDevuelve400() throws Exception {
        mvc.perform(get("/api/v1/productos/keywords"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productoService);
    }
}
