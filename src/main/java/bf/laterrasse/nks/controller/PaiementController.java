package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.paiement.InitierPaiementRequest;
import bf.laterrasse.nks.dto.paiement.InitierPaiementResponse;
import bf.laterrasse.nks.dto.paiement.PaiementResponse;
import bf.laterrasse.nks.dto.paiement.StatutPublicPaiementResponse;
import bf.laterrasse.nks.exception.AccesRefuseException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.PaiementRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.PaiementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/** §13.7. */
@RestController
@RequestMapping("/paiements")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;
    private final PaiementRepository paiementRepository;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/initier")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InitierPaiementResponse> initier(@Valid @RequestBody InitierPaiementRequest request) {
        var utilisateur = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(paiementService.initier(request, utilisateur));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ResponseEntity<PaiementResponse> detail(@PathVariable UUID id) {
        var paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        boolean estAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        if (!estAdmin) {
            UUID currentUserId = currentUserProvider.getCurrentUserId();
            if (paiement.getUtilisateur() == null || !paiement.getUtilisateur().getId().equals(currentUserId)) {
                throw new AccesRefuseException("Accès refusé : ce paiement ne vous appartient pas");
            }
        }

        return ResponseEntity.ok(PaiementResponse.from(paiement));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<PaiementResponse>> lister(Pageable pageable) {
        Page<PaiementResponse> result = paiementRepository.findAll(pageable).map(PaiementResponse::from);
        return ResponseEntity.ok(result);
    }

    /** Endpoint public (sans auth) — utilisé par le frontend pour afficher le résultat du paiement. */
    @GetMapping("/{id}/statut-public")
    @Transactional(readOnly = true)
    public ResponseEntity<StatutPublicPaiementResponse> statutPublic(@PathVariable UUID id) {
        var paiement = paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));
        return ResponseEntity.ok(StatutPublicPaiementResponse.from(paiement, null));
    }

    @PutMapping("/{id}/confirmer-manuellement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<PaiementResponse> confirmerManuellement(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(PaiementResponse.from(paiementService.confirmerManuellement(id, body.get("reference"))));
    }
}
