package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.SnapshotVotesSoiree;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SnapshotVotesSoireeRepository extends JpaRepository<SnapshotVotesSoiree, UUID> {
    List<SnapshotVotesSoiree> findBySoireeId(UUID soireeId);
    Optional<SnapshotVotesSoiree> findBySoireeIdAndCandidatId(UUID soireeId, UUID candidatId);
    boolean existsBySoireeId(UUID soireeId);
}
