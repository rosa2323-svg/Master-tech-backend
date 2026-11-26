package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utp.mastertech.mastertech.model.PuntoReparto;
import java.util.List;

public interface PuntoRepartoRepository extends JpaRepository<PuntoReparto, Long> {

    List<PuntoReparto> findAllByOrderByIdAsc();

    List<PuntoReparto> findByActivoTrue();
}
