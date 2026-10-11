package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Duo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DuoRepository extends JpaRepository<Duo, UUID> {
    List<Duo> findByPhaseId(UUID phaseId);
    List<Duo> findBySoireeId(UUID soireeId);

    @Query("SELECT d FROM Duo d JOIN FETCH d.phase LEFT JOIN FETCH d.soiree JOIN FETCH d.candidat1 c1 JOIN FETCH c1.utilisateur JOIN FETCH d.candidat2 c2 JOIN FETCH c2.utilisateur WHERE d.phase.id = :phaseId")
    List<Duo> findByPhaseIdWithDetails(@Param("phaseId") UUID phaseId);

    @Query("SELECT d FROM Duo d JOIN FETCH d.phase LEFT JOIN FETCH d.soiree JOIN FETCH d.candidat1 c1 JOIN FETCH c1.utilisateur JOIN FETCH d.candidat2 c2 JOIN FETCH c2.utilisateur WHERE d.id = :id")
    Optional<Duo> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT d FROM Duo d WHERE d.phase.id = :phaseId AND (d.candidat1.id = :candidatId OR d.candidat2.id = :candidatId)")
    Optional<Duo> findByPhaseIdAndCandidatId(UUID phaseId, UUID candidatId);

    /** Tous les duos d'un candidat, toutes phases confondues. */
    @Query("SELECT d FROM Duo d WHERE d.candidat1.id = :candidatId OR d.candidat2.id = :candidatId")
    List<Duo> findByCandidatId(UUID candidatId);
}
