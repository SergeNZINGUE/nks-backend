package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.moment.CreerMomentAdminRequest;
import bf.laterrasse.nks.dto.moment.CreerMomentCandidatRequest;
import bf.laterrasse.nks.dto.moment.MomentEvenementResponse;
import bf.laterrasse.nks.dto.moment.RejeterMomentRequest;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.MomentEvenementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * "Moments de l'événement" — galerie publique alimentée par les candidats (modérée) et
 * par l'admin/organisateur (publication directe). Cf. MomentEvenement pour le détail du
 * modèle de données.
 */
@RestController
@RequiredArgsConstructor
public class MomentEvenementController {

    private final MomentEvenementService momentService;
    private final CurrentUserProvider currentUserProvider;

    /** Public — galerie /galerie, onglet "Moments de l'événement" : uniquement les médias VALIDE. */
    @GetMapping("/moments-evenement")
    public ResponseEntity<Page<MomentEvenementResponse>> lister(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return ResponseEntity.ok(momentService.listerPublic(page, size));
    }

    @PostMapping("/moments-evenement")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<MomentEvenementResponse> ajouterCandidat(@Valid @RequestBody CreerMomentCandidatRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(201).body(momentService.creerParCandidat(utilisateurId, request));
    }

    @GetMapping("/moments-evenement/mes-envois")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<List<MomentEvenementResponse>> mesEnvois() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.ok(momentService.listerPourCandidat(utilisateurId));
    }

    @DeleteMapping("/moments-evenement/{id}")
    @PreAuthorize("hasRole('CANDIDAT')")
    public ResponseEntity<Void> retirer(@PathVariable UUID id) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        momentService.retirerParCandidat(utilisateurId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/moments-evenement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<MomentEvenementResponse> ajouterAdmin(@Valid @RequestBody CreerMomentAdminRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(201).body(momentService.creerParAdmin(utilisateurId, request));
    }

    @GetMapping("/admin/moments-evenement/en-attente")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<List<MomentEvenementResponse>> enAttente() {
        return ResponseEntity.ok(momentService.listerEnAttente());
    }

    /** Léger, dédié au badge de la sidebar — jamais la liste complète juste pour un chiffre. */
    @GetMapping("/admin/moments-evenement/en-attente/nombre")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<Long> nombreEnAttente() {
        return ResponseEntity.ok(momentService.compterEnAttente());
    }

    @PutMapping("/admin/moments-evenement/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<MomentEvenementResponse> valider(@PathVariable UUID id) {
        return ResponseEntity.ok(momentService.valider(id));
    }

    @PutMapping("/admin/moments-evenement/{id}/rejeter")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<MomentEvenementResponse> rejeter(@PathVariable UUID id, @Valid @RequestBody RejeterMomentRequest request) {
        return ResponseEntity.ok(momentService.rejeter(id, request.motif()));
    }

    /** Toujours admin/organisateur — jamais accessible au candidat sur son propre moment (décision produit). */
    @PutMapping("/admin/moments-evenement/{id}/mettre-a-la-une")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    public ResponseEntity<MomentEvenementResponse> mettreALaUne(@PathVariable UUID id) {
        return ResponseEntity.ok(momentService.mettreALaUne(id));
    }
}
