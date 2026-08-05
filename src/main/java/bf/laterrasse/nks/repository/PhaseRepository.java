package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Phase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhaseRepository extends JpaRepository<Phase, UUID> {
    List<Phase> findByEditionIdOrderByOrdreAsc(UUID editionId);
}
