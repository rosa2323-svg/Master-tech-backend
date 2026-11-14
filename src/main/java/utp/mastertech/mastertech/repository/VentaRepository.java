package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import utp.mastertech.mastertech.model.Usuario;
import utp.mastertech.mastertech.model.Venta;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {


    // Para el historial de compras del usuario (más reciente primero)
    List<Venta> findByUsuarioOrderByFechaDesc(Usuario usuario);

    Optional<Venta> findByNumeroOrden(String numeroOrden);


    @Query("SELECT v FROM Venta v LEFT JOIN v.usuario u WHERE " +
            "CAST(v.id AS string) LIKE %:keyword% OR " +
            "LOWER(u.nombre) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(v.numeroOrden) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Venta> findByKeyword(@Param("keyword") String keyword);
    @Query(value = """
            SELECT CAST(date_trunc(CAST(:granularidad AS text), v.fecha) AS date) AS periodo,
                   COUNT(*)                           AS ordenes,
                   COALESCE(SUM(v.cantidad_items), 0) AS unidades,
                   COALESCE(SUM(v.total), 0)          AS ingreso
            FROM ventas v
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<TendenciaVentaProjection> tendenciaVentas(@Param("granularidad") String granularidad,
                                                   @Param("desde") LocalDateTime desde,
                                                   @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT COALESCE(SUM(v.total), 0)
            FROM ventas v
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            """, nativeQuery = true)
    BigDecimal ingresoEntre(@Param("desde") LocalDateTime desde,
                            @Param("hasta") LocalDateTime hasta);

    @Query(value = """
            SELECT COUNT(*)
            FROM ventas v
            WHERE v.fecha >= :desde
              AND v.fecha <  :hasta
              AND v.estado <> 'ANULADA'
            """, nativeQuery = true)
    Long ordenesEntre(@Param("desde") LocalDateTime desde,
                      @Param("hasta") LocalDateTime hasta);


}
