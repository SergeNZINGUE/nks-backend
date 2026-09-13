package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ChoixTitreCandidat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChoixTitreCandidatRepository extends JpaRepository<ChoixTitreCandidat, UUID> {
    Optional<ChoixTitreCandidat> findByCandidatIdAndSoireeId(UUID candidatId, UUID soireeId);

    List<ChoixTitreCandidat> findBySoireeIdIn(List<UUID> soireeIds);
}
