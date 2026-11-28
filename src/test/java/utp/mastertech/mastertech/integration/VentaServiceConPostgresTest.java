package utp.mastertech.mastertech.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import utp.mastertech.mastertech.dto.ItemPedidoDTO;
import utp.mastertech.mastertech.exception.StockInsuficienteException;
import utp.mastertech.mastertech.model.Categoria;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import utp.mastertech.mastertech.repository.CategoriaRepository;
import utp.mastertech.mastertech.repository.DetalleVentaRepository;
import utp.mastertech.mastertech.repository.EnvioEventoRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.repository.UsuarioRepository;
import utp.mastertech.mastertech.repository.VentaRepository;
import utp.mastertech.mastertech.service.VentaService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas contra un PostgreSQL REAL (bloqueo de filas, transacciones y restricciones únicas), que con mocks no se ven.
 *
 * Solo corren si existe la variable de entorno TEST_DB_URL (en GitHub Actions la define el workflow con un
 * contenedor de PostgreSQL). Localmente se omiten. El esquema se crea y se borra solo con las entidades
 * (ddl-auto=create-drop): NUNCA apuntar TEST_DB_URL a la base real de Supabase.
 */
@DataJpaTest
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@TestPropertySource(properties = {
        "spring.test.database.replace=none",
        "spring.datasource.url=${TEST_DB_URL}",
        "spring.datasource.username=${TEST_DB_USERNAME:postgres}",
        "spring.datasource.password=${TEST_DB_PASSWORD:postgres}",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(VentaService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED) // transacciones reales y confirmadas, para poder probar concurrencia
class VentaServiceConPostgresTest {

    @Autowired
    private VentaService ventaService;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private VentaRepository ventaRepository;
    @Autowired
    private DetalleVentaRepository detalleVentaRepository;
    @Autowired
    private EnvioEventoRepository envioEventoRepository;

    private Long productoId;
    private Usuario cliente;

    @BeforeEach
    void datos() {
        Categoria categoria = new Categoria();
        categoria.setCodigo("CAT-T1");
        categoria.setNombre("Pruebas");
        categoria = categoriaRepository.save(categoria);

        Producto p = new Producto();
        p.setNombre("Mouse de prueba");
        p.setCodigo("PRD-T1");
        p.setPrecio(10.0);
        p.setStock(1);
        p.setCategoria(categoria);
        productoId = productoRepository.save(p).getId();

        Usuario u = new Usuario();
        u.setNombre("Ana Prueba");
        u.setEmail("ana@prueba.com");
        u.setPassword("x");
        cliente = usuarioRepository.save(u);
    }

    @AfterEach
    void limpiar() {
        envioEventoRepository.deleteAll();
        detalleVentaRepository.deleteAll();
        ventaRepository.deleteAll();
        productoRepository.deleteAll();
        categoriaRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private List<ItemPedidoDTO> items(int cantidad) {
        ItemPedidoDTO i = new ItemPedidoDTO();
        i.setProductoId(productoId);
        i.setCantidad(cantidad);
        return List.of(i);
    }

    /**
     * Texto corto de los resultados para los mensajes de error. No se puede concatenar una Venta directamente:
     * su toString (Lombok) recorre venta -> detalles -> venta... sin fin y provoca StackOverflowError.
     */
    private static String resumen(List<Object> resultados) {
        return resultados.stream()
                .map(r -> r instanceof Venta v ? "Venta#" + v.getId() : r.getClass().getSimpleName())
                .toList()
                .toString();
    }

    /** Lanza las tareas a la vez y devuelve el resultado de cada una (o la excepción que lanzó). */
    private List<Object> enParalelo(List<Callable<Venta>> tareas) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
        CyclicBarrier salida = new CyclicBarrier(tareas.size());
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (Callable<Venta> tarea : tareas) {
                futuros.add(pool.submit(() -> {
                    salida.await();
                    try {
                        return tarea.call();
                    } catch (Exception e) {
                        return e;
                    }
                }));
            }
            List<Object> resultados = new ArrayList<>();
            for (Future<Object> f : futuros) {
                resultados.add(f.get());
            }
            return resultados;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void dosComprasDelUltimoProductoSoloDejanPasarUna() throws Exception {
        List<Object> resultados = enParalelo(List.of(
                () -> ventaService.procesarVenta(items(1), cliente, "MT-A0001"),
                () -> ventaService.procesarVenta(items(1), cliente, "MT-A0002")));

        long ventas = resultados.stream().filter(r -> r instanceof Venta).count();
        long sinStock = resultados.stream().filter(r -> r instanceof StockInsuficienteException).count();
        assertEquals(1, ventas, () -> "debe registrarse exactamente una venta: " + resumen(resultados));
        assertEquals(1, sinStock, () -> "la otra debe fallar por stock: " + resumen(resultados));
        assertEquals(0, productoRepository.findById(productoId).orElseThrow().getStock());
        assertEquals(1, ventaRepository.count());
    }

    @Test
    void variasComprasSimultaneasNuncaDejanElStockNegativo() throws Exception {
        Producto p = productoRepository.findById(productoId).orElseThrow();
        p.setStock(3);
        productoRepository.save(p);

        List<Callable<Venta>> tareas = new ArrayList<>();
        for (int n = 1; n <= 6; n++) {
            String orden = "MT-B000" + n;
            tareas.add(() -> ventaService.procesarVenta(items(1), cliente, orden));
        }
        List<Object> resultados = enParalelo(tareas);

        long ventas = resultados.stream().filter(r -> r instanceof Venta).count();
        assertEquals(3, ventas, () -> "con stock 3 solo 3 compras pueden pasar: " + resumen(resultados));
        assertEquals(0, productoRepository.findById(productoId).orElseThrow().getStock());
        assertEquals(3, ventaRepository.count());
    }

    @Test
    void elMismoNumeroDeOrdenNoDescuentaElStockDosVeces() throws Exception {
        Producto p = productoRepository.findById(productoId).orElseThrow();
        p.setStock(5);
        productoRepository.save(p);

        // Como si /confirmar y el webhook de Stripe registraran el mismo pago a la vez.
        enParalelo(List.of(
                () -> ventaService.procesarVenta(items(1), cliente, "MT-C0001"),
                () -> ventaService.procesarVenta(items(1), cliente, "MT-C0001")));

        assertEquals(1, ventaRepository.count(), "una sola venta para esa orden");
        assertEquals(4, productoRepository.findById(productoId).orElseThrow().getStock(), "el stock se descuenta una sola vez");
    }

    @Test
    void repetirLaMismaOrdenDespuesDevuelveLaVentaExistente() {
        Venta primera = ventaService.procesarVenta(items(1), cliente, "MT-D0001");
        Venta segunda = ventaService.procesarVenta(items(1), cliente, "MT-D0001");

        assertEquals(primera.getId(), segunda.getId());
        assertEquals(0, productoRepository.findById(productoId).orElseThrow().getStock());
    }

    @Test
    void unaCompraQueFallaNoDescuentaStockDeNingunProducto() {
        Producto otro = new Producto();
        otro.setNombre("Teclado de prueba");
        otro.setCodigo("PRD-T2");
        otro.setPrecio(20.0);
        otro.setStock(1);
        otro.setCategoria(categoriaRepository.findAll().get(0));
        Long otroId = productoRepository.save(otro).getId();

        ItemPedidoDTO mouse = new ItemPedidoDTO();
        mouse.setProductoId(productoId);
        mouse.setCantidad(1);
        ItemPedidoDTO teclado = new ItemPedidoDTO();
        teclado.setProductoId(otroId);
        teclado.setCantidad(5); // no alcanza

        assertThrows(StockInsuficienteException.class,
                () -> ventaService.procesarVenta(List.of(mouse, teclado), cliente, "MT-E0001"));

        assertEquals(1, productoRepository.findById(productoId).orElseThrow().getStock(), "el mouse no debe descontarse");
        assertEquals(1, productoRepository.findById(otroId).orElseThrow().getStock());
        assertEquals(0, ventaRepository.count());
    }

    @Test
    void elCatalogoPublicoSoloTraeProductosHabilitados() {
        Producto p = productoRepository.findById(productoId).orElseThrow();
        p.setActivo(false);
        productoRepository.save(p);

        assertTrue(productoRepository.findByActivoTrue().isEmpty());
        assertEquals(1, productoRepository.findAll().size());
    }
}
