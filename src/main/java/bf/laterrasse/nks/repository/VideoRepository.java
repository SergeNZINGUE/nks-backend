package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Video;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository extends JpaRepository<Video, UUID> {
    List<Video> findByCandidatId(UUID candidatId);
    Optional<Video> findByCandidatIdAndPhaseId(UUID candidatId, UUID phaseId);
    List<Video> findByCandidatIdAndPhaseIdIsNull(UUID candidatId);
}
