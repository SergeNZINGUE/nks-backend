package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.SponsorPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SponsorPlacementRepository extends JpaRepository<SponsorPlacement, UUID> {
    List<SponsorPlacement> findByEditionIdOrderByOrdreAffichageAsc(UUID editionId);
}
