package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Paiement;
import bf.laterrasse.nks.dto.paiement.InitierPaiementRequest;
import bf.laterrasse.nks.dto.paiement.InitierPaiementResponse;
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
    public ResponseEntity<Paiement> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(paiementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable")));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Page<Paiement>> lister(Pageable pageable) {
        return ResponseEntity.ok(paiementRepository.findAll(pageable));
    }

    @PutMapping("/{id}/confirmer-manuellement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Paiement> confirmerManuellement(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(paiementService.confirmerManuellement(id, body.get("reference")));
    }
}
