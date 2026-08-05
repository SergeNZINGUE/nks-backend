package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.CritereNotation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CritereNotationRepository extends JpaRepository<CritereNotation, UUID> {
    List<CritereNotation> findByEditionIdAndActifTrueOrderByOrdreAsc(UUID editionId);
}
