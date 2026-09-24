package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.dto.billetterie.AnnulerReservationRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationPublicResponse;
import bf.laterrasse.nks.dto.billetterie.ReservationRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationResponse;
import bf.laterrasse.nks.dto.billetterie.TicketAvecQrResponse;
import bf.laterrasse.nks.dto.billetterie.TicketsGratuitsRequest;
import bf.laterrasse.nks.dto.votesurplace.ReconciliationVoteResponse;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.security.TicketAccessTokenService;
import bf.laterrasse.nks.service.BilletterieService;
import bf.laterrasse.nks.service.VoteSurPlaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * §13.11 — WF-09/WF-10/WF-14. Correctif audit sécurité (IDOR) : {@code mesTickets},
 * {@code ticket} et {@code annuler} exigent désormais un jeton {@code X-Ticket-Access-Token}
 * valide (émis par {@code OtpTicketController}/{@code initierReservation}) — le paramètre
 * {@code telephone} reste présent pour compat/lisibilité mais ne fait plus foi seul.
 */
@RestController
@RequiredArgsConstructor
public class BilletterieController {

    private static final String HEADER_TICKET_ACCESS_TOKEN = "X-Ticket-Access-Token";

    private final BilletterieService billetterieService;
    private final VoteSurPlaceService voteSurPlaceService;
    private final CategorieTicketRepository categorieTicketRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TicketAccessTokenService ticketAccessTokenService;

    @GetMapping("/soirees/{id}/disponibilite")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CategorieTicket>> disponibilite(@PathVariable UUID id) {
        return ResponseEntity.ok(categorieTicketRepository.findBySoireeId(id));
    }

    @PostMapping("/reservations/initier")
    public ResponseEntity<ReservationResponse> initier(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(billetterieService.initierReservation(request));
    }

    @GetMapping("/reservations/mes-tickets")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReservationPublicResponse>> mesTickets(
            @RequestParam String telephone,
            @RequestHeader(name = HEADER_TICKET_ACCESS_TOKEN, required = false) String accessToken) {
        String telephoneNormalise = SmsGateway.normaliserTelephone(telephone);
        // reservationIdAttendu=null exige explicitement un jeton phone-wide (post-OTP) —
        // le jeton reservation-scoped du post-achat ne couvre pas la liste complète.
        ticketAccessTokenService.verifier(accessToken, "read", telephoneNormalise, null);

        List<ReservationPublicResponse> result = reservationRepository.findByTelephoneReservant(telephoneNormalise)
                .stream()
                .map(ReservationPublicResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/reservations/{reservationId}/ticket")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TicketAvecQrResponse>> ticket(
            @PathVariable UUID reservationId,
            @RequestParam String telephone,
            @RequestHeader(name = HEADER_TICKET_ACCESS_TOKEN, required = false) String accessToken) {
        String telephoneNormalise = SmsGateway.normaliserTelephone(telephone);
        // Accepte un jeton phone-wide (post-OTP) OU un jeton scopé exactement à cette réservation.
        ticketAccessTokenService.verifier(accessToken, "read", telephoneNormalise, reservationId);
        return ResponseEntity.ok(billetterieService.obtenirTicketsAvecQr(reservationId, telephoneNormalise));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> annuler(
            @PathVariable UUID id,
            @Valid @RequestBody AnnulerReservationRequest request,
            @RequestHeader(name = HEADER_TICKET_ACCESS_TOKEN, required = false) String accessToken) {
        String telephoneNormalise = SmsGateway.normaliserTelephone(request.telephone());
        // scope "cancel" uniquement porté par le jeton phone-wide post-OTP — jamais par le
        // jeton post-achat immédiat (scope "read" seul) : faille IDOR destructive de l'audit.
        ticketAccessTokenService.verifier(accessToken, "cancel", telephoneNormalise, null);
        billetterieService.annulerReservation(id, telephoneNormalise);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/billetterie/tickets-gratuits")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<ReservationPublicResponse> ticketsGratuits(@Valid @RequestBody TicketsGratuitsRequest body) {
        var admin = currentUserProvider.getCurrentUser();
        ReservationPublicResponse result = ReservationPublicResponse.from(
                billetterieService.genererTicketsGratuits(body.soireeId(), body.categorieId(), body.nom(),
                        body.telephone(), body.nbPlaces(), body.beneficiaires(), admin));
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/admin/billetterie/reservations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ReservationPublicResponse>> reservationsAdmin(@RequestParam UUID soireeId, Pageable pageable) {
        Page<ReservationPublicResponse> result = reservationRepository.findBySoireeId(soireeId, pageable)
                .map(ReservationPublicResponse::from);
        return ResponseEntity.ok(result);
    }

    /**
     * Réconciliation admin en cas de contestation d'un vote sur place — croise le téléphone
     * déclaré au vote (jamais vérifié en temps réel) avec le vrai téléphone du billet.
     */
    @GetMapping("/admin/billetterie/soiree/{soireeId}/reconciliation-votes")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReconciliationVoteResponse>> reconciliationVotes(@PathVariable UUID soireeId) {
        return ResponseEntity.ok(voteSurPlaceService.reconciliationVotes(soireeId));
    }

    @PostMapping("/admin/billetterie/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<CategorieTicket> creerCategorie(@RequestBody CategorieTicket categorie) {
        categorie.setId(null);
        return ResponseEntity.status(201).body(categorieTicketRepository.save(categorie));
    }
}
