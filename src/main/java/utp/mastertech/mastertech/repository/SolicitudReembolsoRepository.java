package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utp.mastertech.mastertech.model.SolicitudReembolso;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SolicitudReembolsoRepository extends JpaRepository<SolicitudReembolso, Long> {

    String CON_DATOS = "SELECT s FROM SolicitudReembolso s JOIN FETCH s.venta v JOIN FETCH s.usuario u LEFT JOIN FETCH s.resueltoPor r ";

    /** Pendientes primero y luego por fecha; trae las relaciones LAZY (open-in-view está desactivado). */
    @Query(CON_DATOS + "ORDER BY CASE WHEN s.estado = 'PENDIENTE' THEN 0 ELSE 1 END, s.fechaSolicitud DESC")
    List<SolicitudReembolso> findTodas();

    @Query(CON_DATOS + "WHERE s.estado = :estado ORDER BY s.fechaSolicitud DESC")
    List<SolicitudReembolso> findPorEstado(@Param("estado") String estado);

    @Query(CON_DATOS + "WHERE LOWER(u.email) = LOWER(:email) ORDER BY s.fechaSolicitud DESC")
    List<SolicitudReembolso> findDelCliente(@Param("email") String email);

    @Query(CON_DATOS + "WHERE s.id = :id")
    Optional<SolicitudReembolso> findConDatos(@Param("id") Long id);

    boolean existsByVentaIdAndEstado(Long ventaId, String estado);

    @Query("SELECT s.venta.id FROM SolicitudReembolso s WHERE s.estado = 'PENDIENTE'")
    Set<Long> findVentaIdsPendientes();
}
