package utp.mastertech.mastertech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utp.mastertech.mastertech.model.DiaNoLaborable;
import java.time.LocalDate;
import java.util.List;

public interface DiaNoLaborableRepository extends JpaRepository<DiaNoLaborable, LocalDate> {

    List<DiaNoLaborable> findAllByOrderByFechaAsc();
}
