package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    List<Media> findByCandidatId(UUID candidatId);
}
