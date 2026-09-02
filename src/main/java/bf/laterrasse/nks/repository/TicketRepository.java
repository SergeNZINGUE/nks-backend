package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
    List<Ticket> findByReservationId(UUID reservationId);
    boolean existsByReservationId(UUID reservationId);
    List<Ticket> findByTelephoneSpectateur(String telephone);
}
