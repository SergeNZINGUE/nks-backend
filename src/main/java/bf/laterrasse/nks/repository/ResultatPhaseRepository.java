package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ResultatPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultatPhaseRepository extends JpaRepository<ResultatPhase, UUID> {
    List<ResultatPhase> findByPhaseIdOrderByRangAsc(UUID phaseId);
    List<ResultatPhase> findByCandidatId(UUID candidatId);
    Optional<ResultatPhase> findByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);
}
