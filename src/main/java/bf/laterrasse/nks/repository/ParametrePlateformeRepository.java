package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ParametrePlateforme;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParametrePlateformeRepository extends JpaRepository<ParametrePlateforme, UUID> {
    Optional<ParametrePlateforme> findByCle(String cle);
}
