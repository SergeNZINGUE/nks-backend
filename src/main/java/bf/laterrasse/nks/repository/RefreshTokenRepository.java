package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHashAndRevoqueFalse(String tokenHash);

    void deleteAllByUtilisateurId(UUID utilisateurId);
}
