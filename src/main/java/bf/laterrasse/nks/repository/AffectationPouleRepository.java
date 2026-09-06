package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.AffectationPoule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AffectationPouleRepository extends JpaRepository<AffectationPoule, UUID> {
    List<AffectationPoule> findByPouleId(UUID pouleId);

    @Query("SELECT a FROM AffectationPoule a JOIN FETCH a.candidat ca JOIN FETCH ca.utilisateur JOIN FETCH a.poule WHERE a.poule.id = :pouleId ORDER BY a.ordrePassage ASC NULLS LAST")
    List<AffectationPoule> findByPouleIdWithDetails(@Param("pouleId") UUID pouleId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM AffectationPoule a WHERE a.poule.soiree.id = :soireeId ORDER BY a.ordrePassage ASC")
    List<AffectationPoule> findByPouleSoireeId(UUID soireeId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(a) > 0 FROM AffectationPoule a WHERE a.candidat.id = :candidatId AND a.poule.phase.id = :phaseId")
    boolean existsByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);
}
