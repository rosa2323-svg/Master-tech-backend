package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utp.mastertech.mastertech.model.EnvioEvento;

import java.util.List;

public interface EnvioEventoRepository extends JpaRepository<EnvioEvento, Long> {

    /** Historial en orden cronológico; trae el usuario (LAZY) porque open-in-view está desactivado. */
    @Query("SELECT e FROM EnvioEvento e LEFT JOIN FETCH e.usuario WHERE e.venta.id = :ventaId ORDER BY e.fecha ASC, e.id ASC")
    List<EnvioEvento> findHistorial(@Param("ventaId") Long ventaId);
}
