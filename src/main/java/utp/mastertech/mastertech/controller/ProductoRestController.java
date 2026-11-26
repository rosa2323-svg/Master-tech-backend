package utp.mastertech.mastertech.controller;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import utp.mastertech.mastertech.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import utp.mastertech.mastertech.dto.CambioActivoDTO;
import utp.mastertech.mastertech.dto.ProductoDTO;
import utp.mastertech.mastertech.mapper.ProductoMapper;
import utp.mastertech.mastertech.model.Categoria;
import utp.mastertech.mastertech.model.Producto;
import utp.mastertech.mastertech.repository.ProductoRepository;
import utp.mastertech.mastertech.service.ProductoService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoRestController {

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ProductoMapper productoMapper;

    /** Solo un ADMIN autenticado puede ver los productos inhabilitados. */
    private static boolean esAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    /**
     * Catálogo público: solo productos habilitados. Con {@code ?todos=true} y un token de ADMIN
     * incluye también los inhabilitados (para el panel de administración).
     */
    @GetMapping
    public List<ProductoDTO> listarTodos(@RequestParam(defaultValue = "false") boolean todos,
                                         Authentication auth) {
        List<Producto> lista = todos && esAdmin(auth)
                ? productoService.listarProductos()
                : productoService.listarProductosActivos();
        return lista.stream()
                .map(productoMapper::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerPorId(@PathVariable Long id, Authentication auth) {
        return productoService.obtenerProducto(id)
                .filter(p -> !Boolean.FALSE.equals(p.getActivo()) || esAdmin(auth))
                .map(productoMapper::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(@Valid @RequestBody ProductoDTO dto) {
        dto.setId(null);
        Producto producto = productoMapper.toEntity(dto);
        productoService.guardarProducto(producto);
        Producto guardado = productoService.obtenerProducto(producto.getId()).orElse(producto);
        return ResponseEntity.status(HttpStatus.CREATED).body(productoMapper.toDTO(guardado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizarProducto(
            @PathVariable Long id,
            @Valid @RequestBody ProductoDTO dto) {
        return productoService.obtenerProducto(id).map(producto -> {
            productoMapper.updateEntityFromDTO(dto, producto);
            productoService.guardarProducto(producto);
            Producto guardado = productoService.obtenerProducto(id).orElse(producto);
            return ResponseEntity.ok(productoMapper.toDTO(guardado));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Sube (o reemplaza) la imagen del producto en Supabase Storage. Solo ADMIN. */
    @PostMapping(value = "/{id}/imagen", consumes = "multipart/form-data")
    public ProductoDTO subirImagen(@PathVariable Long id,
                                   @RequestParam("imagen") MultipartFile imagen) {
        Producto producto = productoService.obtenerProducto(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado: " + id));
        productoService.guardarProducto(producto, imagen);
        Producto guardado = productoService.obtenerProducto(id).orElse(producto);
        return productoMapper.toDTO(guardado);
    }

    /**
     * Habilita o inhabilita un producto (baja lógica). Reemplaza al borrado: el producto no se elimina,
     * así se conserva el historial de ventas y se puede volver a habilitar. Solo ADMIN.
     */
    @PatchMapping("/{id}/activo")
    public ProductoDTO cambiarActivo(@PathVariable Long id, @Valid @RequestBody CambioActivoDTO dto) {
        return productoMapper.toDTO(productoService.cambiarActivo(id, dto.getActivo()));
    }

    @GetMapping("/keywords")
    public ResponseEntity<List<String>> getKeywords(@RequestParam String term) {
        String lowerTerm = term.toLowerCase();

        List<String> productos = productoService.listarProductosActivos().stream()
                .filter(p -> (p.getNombre() != null && p.getNombre().toLowerCase().contains(lowerTerm)) ||
                        (p.getCategoria() != null && p.getCategoria().getNombre().toLowerCase().contains(lowerTerm)))
                .map(p -> p.getNombre())
                .distinct()
                .limit(4)
                .collect(Collectors.toList());

        List<String> categorias = productoService.listarCategorias().stream()
                .filter(c -> c.getNombre() != null && c.getNombre().toLowerCase().contains(lowerTerm))
                .map(c -> c.getNombre())
                .distinct()
                .limit(2)
                .collect(Collectors.toList());

        productos.addAll(categorias);
        return ResponseEntity.ok(productos);
    }
}