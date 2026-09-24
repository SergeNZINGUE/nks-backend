package bf.laterrasse.nks.job;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Reservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.ReservationBeneficiaireRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private final ReservationBeneficiaireRepository reservationBeneficiaireRepository;

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expirerPreReservations() {
        List<Reservation> expirees = reservationRepository
                .findByStatutAndDateExpirationBefore(StatutReservation.PENDING, Instant.now());

        for (Reservation reservation : expirees) {
            reservation.setStatut(StatutReservation.EXPIREE);
            reservationRepository.save(reservation);
            // La pré-réservation expire : les numéros de ses billets redeviennent disponibles.
            reservationBeneficiaireRepository.desactiverParReservation(reservation.getId());

            // Catégorie stockée sur la réservation : plus de "1ère catégorie de la soirée"
            // devinée à l'aveugle. Verrou pessimiste avant décrément (comme le fait déjà
            // BilletterieService.reactiverReservationExpiree pour les autres libérations de places) —
            // le job n'en posait aucun auparavant.
            UUID categorieId = resoudreCategorieId(reservation);
            if (categorieId == null) {
                continue;
            }
            categorieTicketRepository.findByIdForUpdate(categorieId)
                    .ifPresent(categorie -> liberer(categorie, reservation.getNbPlaces()));
        }
        if (!expirees.isEmpty()) {
            log.info("{} pré-réservation(s) expirée(s), places libérées", expirees.size());
        }
    }

    /**
     * Repli de secours UNIQUEMENT pour une réservation legacy dont {@code categorie_id} serait
     * resté NULL après le backfill de la migration d'ajout de colonne (PENDING créée avant le
     * déploiement de cette colonne, jamais associée à un billet). Devine la catégorie via la
     * 1ère catégorie active de la soirée — risque d'erreur si la soirée a plusieurs catégories actives.
     */
    private UUID resoudreCategorieId(Reservation reservation) {
        if (reservation.getCategorie() != null) {
            return reservation.getCategorie().getId();
        }
        return categorieTicketRepository.findBySoireeId(reservation.getSoiree().getId()).stream()
                .findFirst()
                .map(categorie -> {
                    log.warn("Réservation {} expirée sans catégorie stockée, repli sur la 1ère catégorie de la "
                            + "soirée — risque d'erreur si plusieurs catégories", reservation.getId());
                    return categorie.getId();
                })
                .orElseGet(() -> {
                    log.warn("Réservation {} expirée sans catégorie stockée et aucune catégorie trouvée pour la "
                            + "soirée — place non libérée automatiquement, vérification manuelle requise",
                            reservation.getId());
                    return null;
                });
    }

    private void liberer(CategorieTicket categorie, int nbPlaces) {
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - nbPlaces));
        categorieTicketRepository.save(categorie);
    }
}
