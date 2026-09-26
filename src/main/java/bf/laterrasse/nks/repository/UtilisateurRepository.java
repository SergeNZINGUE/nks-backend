package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

    Optional<Utilisateur> findByEmailIgnoreCaseAndDateSuppressionIsNull(String email);

    Optional<Utilisateur> findByTelephoneAndDateSuppressionIsNull(String telephone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByTelephone(String telephone);

    /**
     * Diffusion des notifications in-app "Moments de l'événement" (nouveau dépôt à
     * modérer) à tous les admins/organisateurs actifs — pas de méthode équivalente
     * avant (les diffusions groupées existantes, cf. CommunicationService, ciblent des
     * candidats/partenaires, jamais le staff par rôle).
     */
    @Query("SELECT DISTINCT u FROM Utilisateur u JOIN u.roles r WHERE r.nom IN :roles AND u.dateSuppression IS NULL")
    List<Utilisateur> findByRolesNomIn(@Param("roles") Collection<RoleName> roles);
}
