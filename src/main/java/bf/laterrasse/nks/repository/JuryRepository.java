package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Jury;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JuryRepository extends JpaRepository<Jury, UUID> {
    Optional<Jury> findByUtilisateurId(UUID utilisateurId);
    List<Jury> findByEditionId(UUID editionId);
}
