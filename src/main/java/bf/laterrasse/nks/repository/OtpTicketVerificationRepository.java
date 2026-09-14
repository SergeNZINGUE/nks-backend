package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.OtpTicketVerification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpTicketVerificationRepository extends JpaRepository<OtpTicketVerification, UUID> {

    Optional<OtpTicketVerification> findFirstByTelephoneAndConsommeFalseOrderByDateCreationDesc(String telephone);

    long deleteByExpireAtBefore(Instant seuil);
}
