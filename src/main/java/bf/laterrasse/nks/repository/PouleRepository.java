package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Poule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PouleRepository extends JpaRepository<Poule, UUID> {
    List<Poule> findByPhaseId(UUID phaseId);
}
