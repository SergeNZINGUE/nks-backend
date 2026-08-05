package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.QRCodeTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface QRCodeTicketRepository extends JpaRepository<QRCodeTicket, UUID> {

    Optional<QRCodeTicket> findByCodeUuid(UUID codeUuid);

    Optional<QRCodeTicket> findByTicketId(UUID ticketId);

    /**
     * Verrou pessimiste sur la ligne QR code pendant la validation du scan (§14.6) :
     * empêche deux scans concurrents du même QR code d'être tous les deux acceptés.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM QRCodeTicket q WHERE q.codeUuid = :codeUuid")
    Optional<QRCodeTicket> findByCodeUuidForUpdate(UUID codeUuid);
}
