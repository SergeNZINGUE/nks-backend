package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.NoteJury;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteJuryRepository extends JpaRepository<NoteJury, UUID> {

    List<NoteJury> findByJuryIdAndSoireeId(UUID juryId, UUID soireeId);

    List<NoteJury> findByCandidatIdAndSoireeId(UUID candidatId, UUID soireeId);

    List<NoteJury> findBySoireeId(UUID soireeId);

    Optional<NoteJury> findByJuryIdAndCandidatIdAndSoireeIdAndCritereId(
            UUID juryId, UUID candidatId, UUID soireeId, UUID critereId);

    boolean existsBySoireeIdAndVerrouilleTrue(UUID soireeId);
}
