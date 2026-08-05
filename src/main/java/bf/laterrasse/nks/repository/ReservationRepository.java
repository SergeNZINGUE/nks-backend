package bf.laterrasse.nks.repository;

import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    List<Reservation> findByTelephoneReservant(String telephone);

    Page<Reservation> findBySoireeId(UUID soireeId, Pageable pageable);

    List<Reservation> findByStatutAndDateExpirationBefore(StatutReservation statut, Instant seuil);

    Optional<Reservation> findByPaiementId(UUID paiementId);
}
