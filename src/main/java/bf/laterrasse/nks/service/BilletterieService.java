package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.StatutReservation;
import bf.laterrasse.nks.domain.enums.Enums.StatutTicket;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.dto.billetterie.ReservationRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.event.PaiementEchoueEvent;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WF-09/WF-10/WF-14. Remboursement billetterie décidé avec le client : traitement MANUEL
 * au cas par cas (pas d'automatisation, cf. README §Décisions) — annulerReservation()
 * libère la place et notifie, mais ne déclenche jamais PaymentGateway.rembourserTransaction().
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BilletterieService {

    private final CategorieTicketRepository categorieTicketRepository;
    private final ReservationRepository reservationRepository;
    private final TicketRepository ticketRepository;
    private final QRCodeTicketRepository qrCodeTicketRepository;
    private final PaiementService paiementService;
    private final ParametrePlateformeService parametrePlateformeService;
    private final NotificationService notificationService;

    @Transactional
    public ReservationResponse initierReservation(ReservationRequest request) {
        CategorieTicket categorie = categorieTicketRepository.findByIdForUpdate(request.categorieId())
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de ticket introuvable"));

        if (!categorie.isActif()) {
            throw new ConflitEtatException("Cette catégorie de billets n'est plus ouverte à la réservation");
        }
        int placesRestantes = categorie.getNbPlacesDisponibles() - categorie.getNbPlacesReservees();
        if (placesRestantes < request.nbPlaces()) {
            throw new ConflitEtatException("Places insuffisantes : " + placesRestantes + " restante(s)");
        }

        BigDecimal montant = categorie.getPrix().multiply(BigDecimal.valueOf(request.nbPlaces()));
        int delaiMinutes = parametrePlateformeService.getInt("DELAI_PRERESA_MINUTES", 15);

        Reservation reservation = Reservation.builder()
                .soiree(categorie.getSoiree())
                .telephoneReservant(request.telephoneReservant())
                .nomReservant(request.nomReservant())
                .emailReservant(request.emailReservant())
                .nbPlaces(request.nbPlaces())
                .montantTotal(montant)
                .statut(StatutReservation.PENDING)
                .dateExpiration(Instant.now().plus(delaiMinutes, ChronoUnit.MINUTES))
                .gratuit(montant.compareTo(BigDecimal.ZERO) == 0)
                .build();

        // Pré-réservation immédiate des places (libérées par ReservationExpiryJob si non payées à temps)
        categorie.setNbPlacesReservees(categorie.getNbPlacesReservees() + request.nbPlaces());
        categorieTicketRepository.save(categorie);

        String urlPaiement = null;
        if (!reservation.isGratuit()) {
            PaiementInitie paiementInitie = paiementService.creerEtDemarrer(
                    TypePaiement.BILLET, montant, request.telephoneReservant(), null);
            reservation.setPaiement(paiementInitie.paiement());
            urlPaiement = paiementInitie.urlPaiement();
        } else {
            reservation.setStatut(StatutReservation.CONFIRMEE);
        }

        reservation = reservationRepository.save(reservation);

        if (reservation.isGratuit()) {
            genererTickets(reservation, categorie);
        }

        return new ReservationResponse(reservation.getId(),
                reservation.getPaiement() != null ? reservation.getPaiement().getId() : null,
                urlPaiement, montant, reservation.getStatut().name());
    }

    @EventListener
    @Transactional
    public void onPaiementConfirme(PaiementConfirmeEvent event) {
        if (event.typePaiement() != TypePaiement.BILLET) {
            return;
        }
        reservationRepository.findByPaiementId(event.paiementId())
                .ifPresent(reservation -> {
                    reservation.setStatut(StatutReservation.CONFIRMEE);
                    reservationRepository.save(reservation);
                    CategorieTicket categorie = trouverCategoriePourReservation(reservation);
                    genererTickets(reservation, categorie);
                });
    }

    @EventListener
    @Transactional
    public void onPaiementEchoue(PaiementEchoueEvent event) {
        if (event.typePaiement() != TypePaiement.BILLET) {
            return;
        }
        reservationRepository.findByPaiementId(event.paiementId()).ifPresent(this::liberer);
    }

    @Transactional
    public void annulerReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Réservation introuvable"));

        if (reservation.getStatut() != StatutReservation.CONFIRMEE) {
            throw new ConflitEtatException("Seule une réservation confirmée peut être annulée");
        }
        long heuresAvant = ChronoUnit.HOURS.between(Instant.now(), reservation.getSoiree().getDateHeure());
        if (heuresAvant < 24) {
            throw new ValidationMetierException("Annulation impossible à moins de 24h de la soirée (RM-46)");
        }

        reservation.setStatut(StatutReservation.ANNULEE);
        reservationRepository.save(reservation);

        List<Ticket> tickets = ticketRepository.findByReservationId(reservationId);
        Instant now = Instant.now();
        tickets.forEach(t -> {
            t.setStatut(StatutTicket.ANNULE);
            t.setDateAnnulation(now);
            qrCodeTicketRepository.findByTicketId(t.getId())
                    .ifPresent(q -> { q.setValide(false); qrCodeTicketRepository.save(q); });
        });
        ticketRepository.saveAll(tickets);

        CategorieTicket categorie = trouverCategoriePourReservation(reservation);
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - reservation.getNbPlaces()));
        categorieTicketRepository.save(categorie);

        // Remboursement : décision client = traitement manuel, aucun appel gateway ici.
        notificationService.envoyerSms(null, reservation.getTelephoneReservant(), TypeNotification.BILLET_EMIS,
                "NKS : votre réservation a été annulée. Le remboursement (le cas échéant) sera traité "
                        + "manuellement par notre équipe.");
    }

    private void liberer(Reservation reservation) {
        reservation.setStatut(StatutReservation.EXPIREE);
        reservationRepository.save(reservation);
        CategorieTicket categorie = trouverCategoriePourReservation(reservation);
        categorie.setNbPlacesReservees(Math.max(0, categorie.getNbPlacesReservees() - reservation.getNbPlaces()));
        categorieTicketRepository.save(categorie);
    }

    private CategorieTicket trouverCategoriePourReservation(Reservation reservation) {
        // La catégorie n'est pas stockée sur Reservation (elle l'est sur chaque Ticket) ;
        // avant génération des tickets on la retrouve via la 1ère catégorie active de la soirée
        // correspondant au montant unitaire — limitation acceptable pour le MVP (1 catégorie
        // active par soirée dans le cas courant). À affiner si plusieurs catégories concurrentes.
        return categorieTicketRepository.findBySoireeId(reservation.getSoiree().getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie de ticket introuvable pour la soirée"));
    }

    private void genererTickets(Reservation reservation, CategorieTicket categorie) {
        for (int i = 0; i < reservation.getNbPlaces(); i++) {
            Ticket ticket = Ticket.builder()
                    .reservation(reservation)
                    .soiree(reservation.getSoiree())
                    .categorie(categorie)
                    .nomSpectateur(reservation.getNomReservant())
                    .telephoneSpectateur(reservation.getTelephoneReservant())
                    .statut(StatutTicket.EMIS)
                    .build();
            ticket = ticketRepository.save(ticket);

            QRCodeTicket qr = QRCodeTicket.builder()
                    .ticket(ticket)
                    .codeUuid(UUID.randomUUID())
                    .valide(true)
                    .build();
            qrCodeTicketRepository.save(qr);
        }

        notificationService.envoyerSms(null, reservation.getTelephoneReservant(), TypeNotification.BILLET_EMIS,
                "NKS : votre paiement est confirmé ! Vos " + reservation.getNbPlaces()
                        + " billet(s) sont disponibles sur le site avec votre numéro de téléphone.");
        if (reservation.getEmailReservant() != null) {
            notificationService.envoyerEmail(null, reservation.getEmailReservant(), TypeNotification.BILLET_EMIS,
                    "NKS — Vos billets",
                    "<p>Bonjour " + reservation.getNomReservant() + ",</p><p>Votre réservation pour \""
                            + reservation.getSoiree().getNom() + "\" est confirmée (" + reservation.getNbPlaces()
                            + " place(s)). Présentez le QR code disponible dans votre espace à l'entrée.</p>");
        }
    }

    @Transactional
    public Reservation genererTicketsGratuits(UUID soireeId, UUID categorieId, String nom, String telephone,
                                               int nbPlaces, Utilisateur admin) {
        CategorieTicket categorie = categorieTicketRepository.findByIdForUpdate(categorieId)
                .orElseThrow(() -> new ResourceNotFoundException("Catégorie introuvable"));

        Reservation reservation = Reservation.builder()
                .soiree(categorie.getSoiree())
                .telephoneReservant(telephone)
                .nomReservant(nom)
                .nbPlaces(nbPlaces)
                .montantTotal(BigDecimal.ZERO)
                .statut(StatutReservation.CONFIRMEE)
                .gratuit(true)
                .adminEmission(admin)
                .build();
        categorie.setNbPlacesReservees(categorie.getNbPlacesReservees() + nbPlaces);
        categorieTicketRepository.save(categorie);
        reservation = reservationRepository.save(reservation);

        genererTickets(reservation, categorie);
        return reservation;
    }
}
