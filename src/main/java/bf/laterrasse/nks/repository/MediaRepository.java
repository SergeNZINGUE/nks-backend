package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Media;
import bf.laterrasse.nks.domain.enums.Enums.TypeMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository extends JpaRepository<Media, UUID> {
    List<Media> findByCandidatId(UUID candidatId);
    Optional<Media> findByCandidatIdAndType(UUID candidatId, TypeMedia type);
}
