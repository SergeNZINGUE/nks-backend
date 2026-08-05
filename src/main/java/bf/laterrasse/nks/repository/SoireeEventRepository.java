package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.SoireeEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SoireeEventRepository extends JpaRepository<SoireeEvent, UUID> {
    List<SoireeEvent> findByPhaseId(UUID phaseId);
    List<SoireeEvent> findByEditionId(UUID editionId);
}
