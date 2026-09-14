package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import bf.laterrasse.nks.domain.enums.Enums.TypeDroitVote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface DroitVoteSurPlaceRepository extends JpaRepository<DroitVoteSurPlace, UUID> {

    /** Un seul droit BASE possible par billet (index unique partiel, cf. V20) — c'est la garantie anti-fraude. */
    boolean existsByTicketIdAndTypeDroit(UUID ticketId, TypeDroitVote typeDroit);

    long countByTicketIdAndTypeDroit(UUID ticketId, TypeDroitVote typeDroit);

    /** Tous les droits (BASE + BONUS confondus) d'un billet, du plus ancien au plus récent. */
    List<DroitVoteSurPlace> findByTicketIdOrderByDateEmissionAsc(UUID ticketId);

    /** Réconciliation admin (contestation) — seuls les droits ayant abouti à un vote ont un intérêt ici. */
    List<DroitVoteSurPlace> findBySoireeIdAndStatutOrderByDateVoteDesc(UUID soireeId, StatutDroitVote statut);

    /**
     * Verrou pessimiste sur les droits DISPONIBLE d'un billet pendant la validation du vote :
     * empêche deux tentatives concurrentes (même billet) d'être toutes les deux acceptées sur
     * le même droit. Le plus ancien droit disponible (BASE en premier, puis BONUS par ordre
     * d'émission) est consommé en priorité.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DroitVoteSurPlace d WHERE d.ticket.id = :ticketId AND d.statut = 'DISPONIBLE' ORDER BY d.dateEmission ASC")
    List<DroitVoteSurPlace> findDisponiblesByTicketIdForUpdate(UUID ticketId);
}
