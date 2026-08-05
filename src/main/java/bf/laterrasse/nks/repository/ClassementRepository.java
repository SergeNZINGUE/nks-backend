package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Classement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassementRepository extends JpaRepository<Classement, UUID> {
    List<Classement> findByEditionIdOrderByRangGlobalAsc(UUID editionId);
    Optional<Classement> findByCandidatIdAndEditionId(UUID candidatId, UUID editionId);
}
