package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ResultatPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultatPhaseRepository extends JpaRepository<ResultatPhase, UUID> {
    List<ResultatPhase> findByPhaseIdOrderByRangAsc(UUID phaseId);
    List<ResultatPhase> findByCandidatId(UUID candidatId);
    Optional<ResultatPhase> findByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);

    /** Résultats visibles par le candidat : uniquement ceux dont la soirée a resultats_publies = true. */
    @Query("""
            SELECT r FROM ResultatPhase r
            WHERE r.candidat.id = :candidatId
              AND EXISTS (
                SELECT 1 FROM SoireeEvent s WHERE s.phase = r.phase AND s.resultatsPublies = true
              )
            """)
    List<ResultatPhase> findByCandidatIdEtResultatsPublies(@Param("candidatId") UUID candidatId);
}
