package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Duo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DuoRepository extends JpaRepository<Duo, UUID> {
    List<Duo> findByPhaseId(UUID phaseId);
    List<Duo> findBySoireeId(UUID soireeId);
}
