package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.domain.enums.Enums.StatutPaiement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaiementRepository extends JpaRepository<Paiement, UUID> {
    Optional<Paiement> findByIdempotencyKey(UUID idempotencyKey);
    Page<Paiement> findByStatut(StatutPaiement statut, Pageable pageable);
}
