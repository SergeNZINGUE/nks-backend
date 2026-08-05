package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EditionRepository extends JpaRepository<Edition, UUID> {
    Optional<Edition> findByStatut(StatutEdition statut);
}
