package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.TitreImpose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TitreImposeRepository extends JpaRepository<TitreImpose, UUID> {
    List<TitreImpose> findByPhaseIdOrderByOrdreAsc(UUID phaseId);
}
