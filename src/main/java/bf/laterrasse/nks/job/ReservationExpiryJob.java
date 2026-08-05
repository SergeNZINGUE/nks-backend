package bf.laterrasse.nks.job;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * WF-09 : "Si paiement non reçu dans les 15 min → pré-réservation annulée, places
 * libérées." Tourne toutes les minutes pour limiter le temps de blocage des places.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReservationExpiryJob {

    private final ReservationRepository reservationRepository;
    private final CategorieTicketRepository categorieTicketRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expirerPreReservations() {
        List<Reservation> expirees = reservationRepository
                .findByStatutAndDateExpirationBefore(StatutReservation.PENDING, Instant.now());

        for (Reservation reservation : expirees) {
            reservation.setStatut(StatutReservation.EXPIREE);
            reservationRepository.save(reservation);

            categorieTicketRepository.findBySoireeId(reservation.getSoiree().getId()).stream()
                    .findFirst()
                    .ifPresent(categorie -> liberer(categorie, reservation.getNbPlaces()));
        }
        if (!expirees.isEmpty()) {
            log.info("{} pré-réservation(s) expirée(s), places libérées", expirees.size());
        }
    }

    private void liberer(CategorieTicket categorie, int nbPlaces) {
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - nbPlaces));
        categorieTicketRepository.save(categorie);
    }
}
