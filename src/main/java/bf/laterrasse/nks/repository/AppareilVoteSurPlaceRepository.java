package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.AppareilVoteSurPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppareilVoteSurPlaceRepository extends JpaRepository<AppareilVoteSurPlace, UUID> {

    /** Billet auquel cet appareil est déjà lié pour cette soirée (lecture JPQL, jamais du cache 1er niveau). */
    @Query("SELECT a.ticketId FROM AppareilVoteSurPlace a "
            + "WHERE a.soireeId = :soireeId AND a.appareilHash = :appareilHash")
    Optional<UUID> findTicketIdBySoireeAndHash(UUID soireeId, String appareilHash);

    /**
     * INSERT ... ON CONFLICT DO NOTHING : une violation d'unicité concurrente n'avorte pas la
     * transaction PostgreSQL (contrairement à une exception), ce qui permet de re-vérifier
     * ensuite quel billet a gagné la course.
     *
     * @return 1 si la ligne a été insérée, 0 si l'appareil était déjà lié pour cette soirée
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO appareils_vote_sur_place "
            + "(id, soiree_id, appareil_hash, ticket_id, premier_vote, ip, user_agent, empreinte) "
            + "VALUES (:id, :soireeId, :appareilHash, :ticketId, now(), :ip, :userAgent, :empreinte) "
            + "ON CONFLICT (soiree_id, appareil_hash) DO NOTHING", nativeQuery = true)
    int insererSiAbsent(UUID id, UUID soireeId, String appareilHash, UUID ticketId,
                        String ip, String userAgent, String empreinte);

    /** Signal souple : autres billets de la soirée votés depuis la même (ip, user-agent, empreinte). */
    @Query("SELECT DISTINCT a.ticketId FROM AppareilVoteSurPlace a "
            + "WHERE a.soireeId = :soireeId AND a.ip = :ip AND a.userAgent = :userAgent "
            + "AND a.empreinte = :empreinte AND a.ticketId <> :ticketId")
    List<UUID> findAutresTicketsMemeSignature(UUID soireeId, String ip, String userAgent,
                                               String empreinte, UUID ticketId);

    /** Libère les appareils liés à des billets annulés/expirés (jamais appelé pour un billet UTILISE). */
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM AppareilVoteSurPlace a WHERE a.ticketId IN :ticketIds")
    int supprimerParTicketIds(Collection<UUID> ticketIds);
}
