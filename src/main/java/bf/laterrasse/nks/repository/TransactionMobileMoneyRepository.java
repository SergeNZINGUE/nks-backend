package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.TransactionMobileMoney;
import bf.laterrasse.nks.domain.enums.Enums.OperateurMobileMoney;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionMobileMoneyRepository extends JpaRepository<TransactionMobileMoney, UUID> {
    Optional<TransactionMobileMoney> findByOperateurAndReferenceOperateur(
            OperateurMobileMoney operateur, String referenceOperateur);
}
