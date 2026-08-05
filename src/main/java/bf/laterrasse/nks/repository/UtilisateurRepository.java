package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByEmailIgnoreCaseAndDateSuppressionIsNull(String email);

    Optional<Utilisateur> findByTelephoneAndDateSuppressionIsNull(String telephone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTelephone(String telephone);
}
