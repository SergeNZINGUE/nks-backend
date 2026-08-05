package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.AffectationPoule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AffectationPouleRepository extends JpaRepository<AffectationPoule, UUID> {
    List<AffectationPoule> findByPouleId(UUID pouleId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT a FROM AffectationPoule a WHERE a.poule.soiree.id = :soireeId ORDER BY a.ordrePassage ASC")
    List<AffectationPoule> findByPouleSoireeId(UUID soireeId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COUNT(a) > 0 FROM AffectationPoule a WHERE a.candidat.id = :candidatId AND a.poule.phase.id = :phaseId")
    boolean existsByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);
}
