package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {
    Optional<Paiement> findByIdempotencyKey(UUID idempotencyKey);
    Optional<Paiement> findByReferenceExterne(String referenceExterne);

    @Query("select p from Paiement p where p.referenceExterne = :token")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Paiement> findByReferenceExterneForUpdate(@Param("token") String token);
    Page<Paiement> findByStatut(StatutPaiement statut, Pageable pageable);

    /** Paiements PENDING éligibles au polling : créés avant :seuil, tentatives < :max, non expirés. */
    @Query("SELECT p FROM Paiement p WHERE p.statut = 'PENDING' AND p.dateCreation < :seuil AND p.nbTentativesPolling < :maxTentatives")
    List<Paiement> findPendingEligiblesPolling(@Param("seuil") Instant seuil, @Param("maxTentatives") int maxTentatives);
}
