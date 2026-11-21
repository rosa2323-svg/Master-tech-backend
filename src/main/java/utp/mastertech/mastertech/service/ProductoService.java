package utp.mastertech.mastertech.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import utp.mastertech.mastertech.model.Categoria;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.repository.CategoriaRepository;
import utp.mastertech.mastertech.repository.ProductoRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private SupabaseStorageService storageService;
    private void asegurarCodigo(Producto producto) {
        if (producto.getCodigo() != null && !producto.getCodigo().isBlank()) return;
        String prefijo = "PRD";
        if (producto.getCategoria() != null && producto.getCategoria().getId() != null) {
            Categoria cat = categoriaRepository.findById(producto.getCategoria().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Categoría no encontrada: " + producto.getCategoria().getId()));
            if (cat.getCodigo() != null && !cat.getCodigo().isBlank()) prefijo = cat.getCodigo();
        }
        producto.setCodigo(String.format(Locale.ROOT, "%s-%04d", prefijo, productoRepository.siguienteNumeroCodigo()));
    }

    static String soloLetras(String nombre) {
        if (nombre == null) return "";
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
    }

    private void asegurarCodigo(Categoria categoria) {
        if (categoria.getCodigo() != null && !categoria.getCodigo().isBlank()) return;
        String letras = soloLetras(categoria.getNombre());
        if (letras.length() < 2) letras = "CAT";
        String base3 = letras.substring(0, Math.min(3, letras.length()));
        List<String> candidatos = new ArrayList<>();
        candidatos.add(base3);
        if (letras.length() >= 4) candidatos.add(letras.substring(0, 4));
        String base2 = letras.substring(0, 2);
        for (int n = 2; n <= 99; n++) candidatos.add(base2 + n);
        for (String c : candidatos) {
            if (!categoriaRepository.existsByCodigo(c)) {
                categoria.setCodigo(c);
                return;
            }
        }
        throw new IllegalStateException("No se pudo generar una abreviatura única para la categoría");
    }

    /** Todos los productos, también los inhabilitados (uso del administrador). */
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }

    /** Solo los productos habilitados (catálogo público). */
    public List<Producto> listarProductosActivos() {
        return productoRepository.findByActivoTrue();
    }

    public List<Producto> buscarProductos(String keyword) {
        if (keyword == null || keyword.isBlank()) return listarProductos();
        return productoRepository.buscarPorNombre(keyword);
    }

    public Optional<Producto> obtenerProducto(Long id) {
        return productoRepository.findById(id);
    }

    /** Guardado sin imagen (lo usa la API REST: conserva la imagenUrl que traiga el producto). */
    public void guardarProducto(Producto producto) {
        asegurarCodigo(producto);
        productoRepository.save(producto);
    }

    /**
     * Guarda el producto y, si llega una imagen, la sube al bucket y escribe la URL
     * resultante en la tabla productos.
     *
     * Orden de operaciones (sin @Transactional a propósito: cada paso confirma por separado
     * para poder compensar):
     *  1. Se sube la imagen nueva al bucket.
     *  2. Se guarda el producto con la URL nueva.
     *     - Si falla el guardado -> se borra la imagen recién subida (compensación).
     *  3. Solo con el guardado confirmado se borra la imagen anterior del bucket.
     */
    public void guardarProducto(Producto producto, MultipartFile imagen) {
        String urlAnterior = null;
        if (producto.getId() != null) {
            // La URL vigente se toma de la BD, no del formulario.
            urlAnterior = productoRepository.findById(producto.getId())
                    .map(Producto::getImagenUrl)
                    .orElse(null);
            producto.setImagenUrl(urlAnterior);
        } else {
            producto.setImagenUrl(null);
        }

        String urlNueva = null;
        if (imagen != null && !imagen.isEmpty()) {
            urlNueva = storageService.subirImagenProducto(imagen);
            producto.setImagenUrl(urlNueva);
        }

        asegurarCodigo(producto);
        try {
            productoRepository.save(producto);
        } catch (RuntimeException e) {
            if (urlNueva != null) {
                storageService.eliminarPorUrl(urlNueva);
            }
            throw e;
        }

        if (urlNueva != null && urlAnterior != null) {
            storageService.eliminarPorUrl(urlAnterior);
        }
    }

    /**
     * Habilita o inhabilita un producto (baja lógica). No se borra la fila ni su imagen: así se conservan
     * las ventas históricas y el producto puede volver a habilitarse.
     */
    public Producto cambiarActivo(Long id, boolean activo) {
        Producto producto = productoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
        producto.setActivo(activo);
        return productoRepository.save(producto);
    }

    public List<Categoria> listarCategorias() {
        return categoriaRepository.findAll();
    }

    public Optional<Categoria> obtenerCategoria(Long id) {
        return categoriaRepository.findById(id);
    }

    public void guardarCategoria(Categoria categoria) {
        asegurarCodigo(categoria);
        categoriaRepository.save(categoria);
    }

    public List<Producto> obtenerStockBajo(Integer limite) {
        return productoRepository.findByStockLessThanEqual(limite);
    }

    public boolean categoriaEstaVacia(Long id) {
        return productoRepository.findByCategoriaId(id).isEmpty();
    }

    /**
     * Sube (o reemplaza) la imagen de la categoría. Mismo orden que en productos:
     * sube la nueva, guarda la URL y solo entonces borra la anterior del bucket
     * (si el guardado falla, se borra la recién subida).
     */
    public void guardarCategoria(Categoria categoria, MultipartFile imagen) {
        String urlAnterior = categoriaRepository.findById(categoria.getId())
                .map(Categoria::getImagenUrl)
                .orElse(null);
        categoria.setImagenUrl(urlAnterior);

        if (imagen == null || imagen.isEmpty()) {
            return;
        }
        String urlNueva = storageService.subirImagenCategoria(imagen);
        categoria.setImagenUrl(urlNueva);
        try {
            categoriaRepository.save(categoria);
        } catch (RuntimeException e) {
            storageService.eliminarPorUrl(urlNueva);
            throw e;
        }
        if (urlAnterior != null) {
            storageService.eliminarPorUrl(urlAnterior);
        }
    }

    /** Quita la imagen de la categoría: primero limpia la columna y luego borra el archivo del bucket. */
    public void quitarImagenCategoria(Categoria categoria) {
        String url = categoria.getImagenUrl();
        categoria.setImagenUrl(null);
        categoriaRepository.save(categoria);
        storageService.eliminarPorUrl(url);
    }

    /** Elimina la categoría y, una vez borrada la fila, su imagen del bucket. */
    public void eliminarCategoria(Long id) {
        String url = categoriaRepository.findById(id)
                .map(Categoria::getImagenUrl)
                .orElse(null);
        categoriaRepository.deleteById(id);
        storageService.eliminarPorUrl(url);
    }
}