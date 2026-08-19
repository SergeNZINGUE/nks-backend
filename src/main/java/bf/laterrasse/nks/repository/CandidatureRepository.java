package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Candidature;
import bf.laterrasse.nks.domain.enums.Enums.StatutCandidature;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CandidatureRepository extends JpaRepository<Candidature, UUID> {

    Page<Candidature> findByStatut(StatutCandidature statut, Pageable pageable);

    Optional<Candidature> findByCandidatIdAndEditionId(UUID candidatId, UUID editionId);

    boolean existsByCandidatIdAndEditionId(UUID candidatId, UUID editionId);

    boolean existsByCandidatIdAndEditionIdAndStatutIn(UUID candidatId, UUID editionId, Collection<StatutCandidature> statuts);
}
