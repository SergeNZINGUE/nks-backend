package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByReservationId(UUID reservationId);
    boolean existsByReservationId(UUID reservationId);
    List<Ticket> findByTelephoneSpectateur(String telephone);
    List<Ticket> findBySoireeIdAndStatut(UUID soireeId, StatutTicket statut);

    /**
     * Verrou pessimiste sur le ticket : sérialise les appels concurrents "ajouter une
     * consommation bonus" sur le même billet, pour que deux hôtesses ne puissent jamais
     * incrémenter le compteur en même temps sans se voir l'une l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :ticketId")
    Optional<Ticket> findByIdForUpdate(UUID ticketId);
}
