package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Poule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PouleRepository extends JpaRepository<Poule, UUID> {
    List<Poule> findByPhaseId(UUID phaseId);

    @Query("SELECT p FROM Poule p JOIN FETCH p.phase LEFT JOIN FETCH p.soiree WHERE p.phase.id = :phaseId ORDER BY p.dateCreation ASC")
    List<Poule> findByPhaseIdWithDetails(@Param("phaseId") UUID phaseId);
}
