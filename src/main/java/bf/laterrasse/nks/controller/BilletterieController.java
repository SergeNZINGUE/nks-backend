package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.CategorieTicket;
import bf.laterrasse.nks.domain.Reservation;
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
    public ResponseEntity<List<Reservation>> mesTickets(@RequestParam String telephone) {
        return ResponseEntity.ok(reservationRepository.findByTelephoneReservant(telephone));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<Void> annuler(@PathVariable UUID id) {
        billetterieService.annulerReservation(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/billetterie/tickets-gratuits")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Reservation> ticketsGratuits(@RequestBody Map<String, Object> body) {
        var admin = currentUserProvider.getCurrentUser();
        UUID soireeId = UUID.fromString((String) body.get("soireeId"));
        UUID categorieId = UUID.fromString((String) body.get("categorieId"));
        String nom = (String) body.get("nom");
        String telephone = (String) body.get("telephone");
        int nbPlaces = ((Number) body.get("nbPlaces")).intValue();
        return ResponseEntity.status(201).body(
                billetterieService.genererTicketsGratuits(soireeId, categorieId, nom, telephone, nbPlaces, admin));
    }

    @GetMapping("/admin/billetterie/reservations")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Page<Reservation>> reservationsAdmin(@RequestParam UUID soireeId, Pageable pageable) {
        return ResponseEntity.ok(reservationRepository.findBySoireeId(soireeId, pageable));
    }

    @PostMapping("/admin/billetterie/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CategorieTicket> creerCategorie(@RequestBody CategorieTicket categorie) {
        categorie.setId(null);
        return ResponseEntity.status(201).body(categorieTicketRepository.save(categorie));
    }
}
