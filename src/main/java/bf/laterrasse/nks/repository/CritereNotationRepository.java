package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.CritereNotation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CritereNotationRepository extends JpaRepository<CritereNotation, UUID> {
    List<CritereNotation> findByEditionIdAndActifTrueOrderByOrdreAsc(UUID editionId);

    @Query("SELECT c FROM CritereNotation c WHERE c.edition.id = (SELECT s.edition.id FROM SoireeEvent s WHERE s.id = :soireeId) AND c.actif = true ORDER BY c.ordre ASC")
    List<CritereNotation> findActiveBySoireeId(@Param("soireeId") UUID soireeId);
}
