package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DroitVoteSurPlaceRepository extends JpaRepository<DroitVoteSurPlace, UUID> {

    boolean existsByTicketId(UUID ticketId);

    Optional<DroitVoteSurPlace> findByTicketId(UUID ticketId);

    /** Réconciliation admin (contestation) — seuls les droits ayant abouti à un vote ont un intérêt ici. */
    List<DroitVoteSurPlace> findBySoireeIdAndStatutOrderByDateVoteDesc(UUID soireeId, StatutDroitVote statut);

    /**
     * Verrou pessimiste sur la ligne du droit de vote pendant la validation du vote :
     * empêche deux tentatives concurrentes (même billet) d'être toutes les deux acceptées.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM DroitVoteSurPlace d WHERE d.ticket.id = :ticketId")
    Optional<DroitVoteSurPlace> findByTicketIdForUpdate(UUID ticketId);
}
