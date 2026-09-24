package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.ReservationBeneficiaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReservationBeneficiaireRepository extends JpaRepository<ReservationBeneficiaire, UUID> {

    List<ReservationBeneficiaire> findByReservationIdOrderByPositionAsc(UUID reservationId);

    /** Numéros (parmi ceux fournis) déjà portés par un bénéficiaire ACTIF de cette soirée. */
    @Query("SELECT DISTINCT b.telephone FROM ReservationBeneficiaire b "
            + "WHERE b.soireeId = :soireeId AND b.actif = true AND b.telephone IN :telephones")
    List<String> findTelephonesActifs(UUID soireeId, Collection<String> telephones);

    /** Libère toutes les places d'une réservation (expiration, annulation, paiement échoué). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ReservationBeneficiaire b SET b.actif = false "
            + "WHERE b.reservationId = :reservationId AND b.actif = true")
    int desactiverParReservation(UUID reservationId);

    /** Libère la place d'un billet précis (le billet i porte le téléphone du bénéficiaire i). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ReservationBeneficiaire b SET b.actif = false "
            + "WHERE b.reservationId = :reservationId AND b.telephone = :telephone AND b.actif = true")
    int desactiverParReservationEtTelephone(UUID reservationId, String telephone);

    /** Idem {@link #findTelephonesActifs} en EXCLUANT une réservation (re-vérification d'une réservation expirée payée). */
    @Query("SELECT DISTINCT b.telephone FROM ReservationBeneficiaire b "
            + "WHERE b.soireeId = :soireeId AND b.actif = true AND b.telephone IN :telephones "
            + "AND b.reservationId <> :reservationId")
    List<String> findTelephonesActifsHorsReservation(UUID soireeId, Collection<String> telephones, UUID reservationId);

    /**
     * Réactive les lignes d'une réservation expirée puis payée. Garde NOT EXISTS : une ligne dont le numéro
     * est redevenu actif ailleurs n'est jamais réactivée (l'appelant compare le nombre de lignes touchées).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE ReservationBeneficiaire b SET b.actif = true "
            + "WHERE b.reservationId = :reservationId AND b.actif = false "
            + "AND NOT EXISTS (SELECT 1 FROM ReservationBeneficiaire o WHERE o.soireeId = b.soireeId "
            + "AND o.telephone = b.telephone AND o.actif = true AND o.reservationId <> b.reservationId)")
    int reactiverParReservation(UUID reservationId);

    /**
     * Verrou applicatif transactionnel (pg_advisory_xact_lock) par soirée : sérialise tous les écrivains de l'index
     * unique partiel dédié (réservation, billets gratuits, réactivation post-paiement) pour qu'aucune violation
     * d'unicité ne puisse survenir — indispensable car le listener de confirmation de paiement s'exécute dans la
     * transaction du paiement, qu'une violation SQL ferait échouer.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:cle))) v", nativeQuery = true)
    Integer verrouillerSoiree(String cle);
}
