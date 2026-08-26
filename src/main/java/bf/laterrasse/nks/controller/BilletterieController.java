package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.dto.billetterie.ReservationPublicResponse;
import bf.laterrasse.nks.dto.billetterie.ReservationRequest;
import bf.laterrasse.nks.dto.billetterie.ReservationResponse;
import bf.laterrasse.nks.repository.CategorieTicketRepository;
import bf.laterrasse.nks.repository.ReservationRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.BilletterieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** §13.11 — WF-09/WF-10/WF-14. */
@RestController
@RequiredArgsConstructor
public class BilletterieController {

    private final BilletterieService billetterieService;
    private final CategorieTicketRepository categorieTicketRepository;
    private final ReservationRepository reservationRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/soirees/{id}/disponibilite")
    public ResponseEntity<List<CategorieTicket>> disponibilite(@PathVariable UUID id) {
        return ResponseEntity.ok(categorieTicketRepository.findBySoireeId(id));
    }

    @PostMapping("/reservations/initier")
    public ResponseEntity<ReservationResponse> initier(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(billetterieService.initierReservation(request));
    }

    @GetMapping("/reservations/mes-tickets")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ReservationPublicResponse>> mesTickets(@RequestParam String telephone) {
        List<ReservationPublicResponse> result = reservationRepository.findByTelephoneReservant(telephone).stream()
                .map(ReservationPublicResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> annuler(@PathVariable UUID id) {
        billetterieService.annulerReservation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/billetterie/tickets-gratuits")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<ReservationPublicResponse> ticketsGratuits(@RequestBody Map<String, Object> body) {
        var admin = currentUserProvider.getCurrentUser();
        UUID soireeId = UUID.fromString((String) body.get("soireeId"));
        UUID categorieId = UUID.fromString((String) body.get("categorieId"));
        String nom = (String) body.get("nom");
        String telephone = (String) body.get("telephone");
        int nbPlaces = ((Number) body.get("nbPlaces")).intValue();
        ReservationPublicResponse result = ReservationPublicResponse.from(
                billetterieService.genererTicketsGratuits(soireeId, categorieId, nom, telephone, nbPlaces, admin));
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

    @PostMapping("/admin/billetterie/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CategorieTicket> creerCategorie(@RequestBody CategorieTicket categorie) {
        categorie.setId(null);
        return ResponseEntity.status(201).body(categorieTicketRepository.save(categorie));
    }
}
