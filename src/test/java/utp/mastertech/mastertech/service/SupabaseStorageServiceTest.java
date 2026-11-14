package utp.mastertech.mastertech.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import utp.mastertech.mastertech.exception.StorageException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba SupabaseStorageService contra un servidor HTTP local (loopback): no toca Internet
 * ni el Supabase real. El servidor guarda lo que recibe para poder verificarlo.
 */
class SupabaseStorageServiceTest {

    private static final String SERVICE_KEY = "clave-de-servicio";
    private static final String BUCKET = "Productos";

    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 0};

    private HttpServer servidor;
    private String baseUrl;
    private SupabaseStorageService service;

    private final AtomicInteger respuesta = new AtomicInteger(200);
    private final AtomicInteger peticiones = new AtomicInteger();
    private final AtomicReference<String> metodo = new AtomicReference<>();
    private final AtomicReference<String> ruta = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> apikey = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private final AtomicReference<byte[]> cuerpo = new AtomicReference<>();

    @BeforeEach
    void iniciarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.createContext("/", exchange -> {
            peticiones.incrementAndGet();
            metodo.set(exchange.getRequestMethod());
            ruta.set(exchange.getRequestURI().getRawPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            apikey.set(exchange.getRequestHeaders().getFirst("apikey"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            cuerpo.set(exchange.getRequestBody().readAllBytes());
            byte[] resp = "{}".getBytes();
            exchange.sendResponseHeaders(respuesta.get(), resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        servidor.start();
        baseUrl = "http://127.0.0.1:" + servidor.getAddress().getPort();
        service = new SupabaseStorageService(baseUrl, SERVICE_KEY, BUCKET);
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private MockMultipartFile archivo(byte[] bytes) {
        return new MockMultipartFile("imagen", "foto.bin", "application/octet-stream", bytes);
    }

    private String prefijoPublico() {
        return baseUrl + "/storage/v1/object/public/" + BUCKET + "/";
    }

    // ---------- validaciones (no llegan a la red) ----------

    @Test
    void archivoNuloLanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.subirImagenProducto(null));

        assertEquals("Selecciona una imagen.", ex.getMessage());
        assertEquals(0, peticiones.get());
    }

    @Test
    void archivoVacioLanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.subirImagenProducto(archivo(new byte[0])));

        assertEquals("Selecciona una imagen.", ex.getMessage());
    }

    @Test
    void archivoMayorA5MbLanzaIllegalArgument() {
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(JPG, 0, grande, 0, JPG.length);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.subirImagenProducto(archivo(grande)));

        assertTrue(ex.getMessage().contains("5 MB"));
        assertEquals(0, peticiones.get());
    }

    @Test
    void archivoDeExactamente5MbSeAcepta() {
        byte[] justo = new byte[5 * 1024 * 1024];
        System.arraycopy(JPG, 0, justo, 0, JPG.length);

        assertDoesNotThrow(() -> service.subirImagenProducto(archivo(justo)));
    }

    @Test
    void formatoNoPermitidoLanzaIllegalArgumentAunqueElNombreDigaJpg() {
        MockMultipartFile pdf = new MockMultipartFile("imagen", "falsa.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.subirImagenProducto(pdf));

        assertEquals("Formato no permitido. Usa JPG, PNG o WEBP.", ex.getMessage());
        assertEquals(0, peticiones.get());
    }

    @Test
    void riffQueNoEsWebpSeRechaza() {
        byte[] wav = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E', 0};

        assertThrows(IllegalArgumentException.class, () -> service.subirImagenProducto(archivo(wav)));
    }

    @Test
    void siNoSePuedeLeerElArchivoLanzaStorageException() throws IOException {
        MultipartFile roto = mock(MultipartFile.class);
        when(roto.isEmpty()).thenReturn(false);
        when(roto.getSize()).thenReturn(10L);
        when(roto.getBytes()).thenThrow(new IOException("disco"));

        StorageException ex = assertThrows(StorageException.class, () -> service.subirImagenProducto(roto));

        assertEquals("No se pudo leer el archivo subido.", ex.getMessage());
    }

    // ---------- subida correcta ----------

    @Test
    void subirJpgEnviaElArchivoYDevuelveSuUrlPublica() {
        String url = service.subirImagenProducto(archivo(JPG));

        assertEquals("POST", metodo.get());
        assertTrue(ruta.get().matches("/storage/v1/object/Productos/prod_[0-9a-f\\-]{36}\\.jpg"), ruta.get());
        assertEquals("image/jpeg", contentType.get());
        assertArrayEquals(JPG, cuerpo.get());
        assertTrue(url.startsWith(prefijoPublico() + "prod_"));
        assertTrue(url.endsWith(".jpg"));
    }

    @Test
    void subirPngYWebpUsanSuExtensionYSuTipoMime() {
        String urlPng = service.subirImagenProducto(archivo(PNG));
        assertTrue(urlPng.endsWith(".png"));
        assertEquals("image/png", contentType.get());

        String urlWebp = service.subirImagenProducto(archivo(WEBP));
        assertTrue(urlWebp.endsWith(".webp"));
        assertEquals("image/webp", contentType.get());
    }

    @Test
    void laSubidaEnviaLasCredencialesDeServicio() {
        service.subirImagenProducto(archivo(PNG));

        assertEquals("Bearer " + SERVICE_KEY, authorization.get());
        assertEquals(SERVICE_KEY, apikey.get());
    }

    @Test
    void cadaSubidaGeneraUnNombreDistinto() {
        String a = service.subirImagenProducto(archivo(JPG));
        String b = service.subirImagenProducto(archivo(JPG));

        assertNotEquals(a, b);
    }

    @Test
    void lasImagenesDeCategoriaUsanElPrefijoCat() {
        String url = service.subirImagenCategoria(archivo(JPG));

        assertTrue(ruta.get().contains("/cat_"), ruta.get());
        assertTrue(url.startsWith(prefijoPublico() + "cat_"));
    }

    @Test
    void laUrlBaseConBarraFinalSeNormaliza() {
        SupabaseStorageService conBarra = new SupabaseStorageService(baseUrl + "/", SERVICE_KEY, BUCKET);

        String url = conBarra.subirImagenProducto(archivo(JPG));

        assertTrue(url.startsWith(prefijoPublico()), url);
        assertFalse(ruta.get().contains("//"));
    }

    @Test
    void elNombreDelBucketSeCodificaEnLaUrl() {
        SupabaseStorageService conEspacio = new SupabaseStorageService(baseUrl, SERVICE_KEY, "Mis Productos");

        String url = conEspacio.subirImagenProducto(archivo(JPG));

        assertTrue(ruta.get().startsWith("/storage/v1/object/Mis%20Productos/"), ruta.get());
        assertTrue(url.contains("/public/Mis%20Productos/"), url);
    }

    // ---------- errores de red ----------

    @Test
    void siSupabaseRechazaLaSubidaLanzaStorageException() {
        respuesta.set(500);

        StorageException ex = assertThrows(StorageException.class, () -> service.subirImagenProducto(archivo(JPG)));

        assertEquals("No se pudo subir la imagen. Inténtalo de nuevo.", ex.getMessage());
    }

    @Test
    void siLasCredencialesSonInvalidasLanzaStorageException() {
        respuesta.set(401);

        assertThrows(StorageException.class, () -> service.subirImagenProducto(archivo(PNG)));
    }

    @Test
    void siNoHayConexionLanzaStorageExceptionConLaCausa() {
        servidor.stop(0);

        StorageException ex = assertThrows(StorageException.class, () -> service.subirImagenProducto(archivo(JPG)));

        assertEquals("No se pudo conectar con el almacenamiento de imágenes.", ex.getMessage());
        assertNotNull(ex.getCause());
    }

    // ---------- esImagenDelBucket ----------

    @Test
    void esImagenDelBucketReconoceSoloUrlsDeNuestroBucket() {
        assertTrue(service.esImagenDelBucket(prefijoPublico() + "prod_abc.jpg"));
        assertFalse(service.esImagenDelBucket(null));
        assertFalse(service.esImagenDelBucket(""));
        assertFalse(service.esImagenDelBucket(prefijoPublico()));            // sin nombre de archivo
        assertFalse(service.esImagenDelBucket("/Imagenes/laptop.jpg"));      // ruta estática del front
        assertFalse(service.esImagenDelBucket("https://otro-sitio.com/x.jpg"));
    }

    // ---------- eliminarPorUrl ----------

    @Test
    void eliminarEnviaDeleteDelArchivoDelBucket() {
        service.eliminarPorUrl(prefijoPublico() + "prod_abc.jpg");

        assertEquals(1, peticiones.get());
        assertEquals("DELETE", metodo.get());
        assertEquals("/storage/v1/object/Productos/prod_abc.jpg", ruta.get());
        assertEquals("Bearer " + SERVICE_KEY, authorization.get());
    }

    @Test
    void eliminarIgnoraUrlsQueNoSonDelBucket() {
        service.eliminarPorUrl(null);
        service.eliminarPorUrl("/Imagenes/laptop.jpg");
        service.eliminarPorUrl("https://otro-sitio.com/x.jpg");

        assertEquals(0, peticiones.get());
    }

    @Test
    void eliminarNuncaLanzaExcepcionAunqueSupabaseFalle() {
        respuesta.set(500);

        assertDoesNotThrow(() -> service.eliminarPorUrl(prefijoPublico() + "prod_abc.jpg"));
        assertEquals(1, peticiones.get());
    }

    @Test
    void eliminarNuncaLanzaExcepcionSiNoHayConexion() {
        servidor.stop(0);

        assertDoesNotThrow(() -> service.eliminarPorUrl(prefijoPublico() + "prod_abc.jpg"));
    }
}
