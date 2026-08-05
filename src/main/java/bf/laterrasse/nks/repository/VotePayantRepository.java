package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.VotePayant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VotePayantRepository extends JpaRepository<VotePayant, UUID> {
    Optional<VotePayant> findByPaiementId(UUID paiementId);
}
