package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.SoireeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SoireeEventRepository extends JpaRepository<SoireeEvent, UUID> {
    List<SoireeEvent> findByPhaseId(UUID phaseId);
    List<SoireeEvent> findByEditionId(UUID editionId);

    /** JOIN FETCH edition + phase pour éviter le N+1 lors du mapping vers SoireeEventResponse. */
    @Query("SELECT s FROM SoireeEvent s JOIN FETCH s.edition LEFT JOIN FETCH s.phase WHERE s.edition.id = :editionId ORDER BY s.dateHeure")
    List<SoireeEvent> findByEditionIdAvecDetails(@Param("editionId") UUID editionId);
}
