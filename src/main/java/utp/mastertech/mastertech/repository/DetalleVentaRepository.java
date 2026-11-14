package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import utp.mastertech.mastertech.model.DetalleVenta;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetalleVentaRepository
        extends JpaRepository<DetalleVenta, Long> {
    List<DetalleVenta> findByVentaId(Long ventaId);
    @Query(value = """
            SELECT c.id                AS id,
                   c.nombre            AS nombre,
                   SUM(d.cantidad)     AS unidades,
                   SUM(d.subtotal)     AS ingreso
            FROM detalle_ventas d
            JOIN ventas v     ON v.id = d.venta_id
            JOIN productos p  ON p.id = d.producto_id
            JOIN categorias c ON c.id = p.categoria_id
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            GROUP BY c.id, c.nombre
            ORDER BY unidades DESC, c.nombre
            """, nativeQuery = true)
    List<VentaAgrupadaProjection> unidadesPorCategoria(@Param("desde") LocalDateTime desde,
                                                       @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT p.id                AS id,
                   p.nombre            AS nombre,
                   SUM(d.cantidad)     AS unidades,
                   SUM(d.subtotal)     AS ingreso
            FROM detalle_ventas d
            JOIN ventas v    ON v.id = d.venta_id
            JOIN productos p ON p.id = d.producto_id
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            GROUP BY p.id, p.nombre
            ORDER BY unidades DESC, p.nombre
            LIMIT :limite
            """, nativeQuery = true)
    List<VentaAgrupadaProjection> topProductos(@Param("desde") LocalDateTime desde,
                                               @Param("hasta") LocalDateTime hasta,
                                               @Param("limite") int limite);

    @Query(value = """
            SELECT p.id                AS id,
                   p.nombre            AS nombre,
                   SUM(d.cantidad)     AS unidades,
                   SUM(d.subtotal)     AS ingreso
            FROM detalle_ventas d
            JOIN ventas v    ON v.id = d.venta_id
            JOIN productos p ON p.id = d.producto_id
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            GROUP BY p.id, p.nombre
            ORDER BY ingreso DESC, p.nombre
            LIMIT :limite
            """, nativeQuery = true)
    List<VentaAgrupadaProjection> topProductosPorIngreso(@Param("desde") LocalDateTime desde,
                                                         @Param("hasta") LocalDateTime hasta,
                                                         @Param("limite") int limite);
}