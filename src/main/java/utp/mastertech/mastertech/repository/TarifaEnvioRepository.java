package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utp.mastertech.mastertech.model.TarifaEnvio;
import java.util.List;

public interface TarifaEnvioRepository extends JpaRepository<TarifaEnvio, Long> {

    List<TarifaEnvio> findAllByOrderByDistanciaDesdeKmAscIdAsc();

    List<TarifaEnvio> findByActivoTrue();
}
