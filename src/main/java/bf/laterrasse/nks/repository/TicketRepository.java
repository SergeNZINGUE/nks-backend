package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Ticket;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByReservationId(UUID reservationId);
    boolean existsByReservationId(UUID reservationId);
    List<Ticket> findByTelephoneSpectateur(String telephone);
    List<Ticket> findBySoireeIdAndStatut(UUID soireeId, StatutTicket statut);

    /**
     * Numéros (parmi ceux fournis) déjà portés par un billet ACTIF (hors statuts exclus,
     * typiquement ANNULE/EXPIRE) de la soirée, toutes réservations et catégories confondues.
     * Compte aussi les billets legacy (qui portent le téléphone du réservant).
     */
    @Query("SELECT DISTINCT t.telephoneSpectateur FROM Ticket t "
            + "WHERE t.soiree.id = :soireeId AND t.statut NOT IN :statutsExclus "
            + "AND t.telephoneSpectateur IN :telephones")
    List<String> findTelephonesPortesParBilletActif(UUID soireeId, Collection<String> telephones,
                                                     Collection<StatutTicket> statutsExclus);

    /** Idem, en EXCLUANT une réservation (re-vérification d'une réservation expirée payée). */
    @Query("SELECT DISTINCT t.telephoneSpectateur FROM Ticket t "
            + "WHERE t.soiree.id = :soireeId AND t.statut NOT IN :statutsExclus "
            + "AND t.telephoneSpectateur IN :telephones AND t.reservation.id <> :reservationId")
    List<String> findTelephonesPortesParBilletActifHorsReservation(UUID soireeId, Collection<String> telephones,
                                                                    Collection<StatutTicket> statutsExclus,
                                                                    UUID reservationId);

    /** Signal souple (vote) : le numéro saisi correspond-il à un AUTRE billet de la soirée ? */
    boolean existsBySoireeIdAndTelephoneSpectateurAndIdNot(UUID soireeId, String telephoneSpectateur, UUID id);

    /**
     * Verrou pessimiste sur le ticket : sérialise les appels concurrents "ajouter une
     * consommation bonus" sur le même billet, pour que deux hôtesses ne puissent jamais
     * incrémenter le compteur en même temps sans se voir l'une l'autre.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Ticket t WHERE t.id = :ticketId")
    Optional<Ticket> findByIdForUpdate(UUID ticketId);
}
