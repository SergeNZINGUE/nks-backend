package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.VotePayant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VotePayantRepository extends JpaRepository<VotePayant, UUID> {
    Optional<VotePayant> findByPaiementId(UUID paiementId);

    /** Somme des votes achetés (confirmés) par un MSISDN payeur réel sur une fenêtre glissante. */
    @Query("""
            SELECT COALESCE(SUM(vp.nombreVotesAchetes), 0) FROM VotePayant vp
            WHERE vp.paiement.statut = 'COMPLETED'
              AND vp.paiement.id IN (
                SELECT tmm.paiement.id FROM TransactionMobileMoney tmm
                WHERE tmm.telephonePayeur = :telephonePayeur AND tmm.dateWebhook > :depuis
              )
            """)
    long sommeVotesConfirmesParTelephonePayeur(@Param("telephonePayeur") String telephonePayeur,
                                               @Param("depuis") Instant depuis);
}
