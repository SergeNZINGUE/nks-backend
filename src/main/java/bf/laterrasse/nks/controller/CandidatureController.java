package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Candidature;
import bf.laterrasse.nks.domain.enums.Enums.StatutCandidature;
import bf.laterrasse.nks.dto.candidature.*;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.CandidatureRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.CandidatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** §13.3 — WF-01, WF-02. */
@RestController
@RequestMapping("/candidatures")
@RequiredArgsConstructor
public class CandidatureController {

    private final CandidatureService candidatureService;
    private final CandidatureRepository candidatureRepository;
    private final CandidatRepository candidatRepository;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ResponseEntity<CandidatureSubmitResponse> soumettre(@Valid @RequestBody CandidatureSubmitRequest request) {
        return ResponseEntity.status(201).body(candidatureService.soumettre(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CandidatureDetailResponse>> lister(
            @RequestParam(required = false) StatutCandidature statut, Pageable pageable) {
        StatutCandidature filtre = statut != null ? statut : StatutCandidature.EN_ATTENTE;
        Page<CandidatureDetailResponse> page = candidatureRepository.findByStatut(filtre, pageable)
                .map(CandidatureDetailResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatureDetailResponse> detail(@PathVariable UUID id) {
        Candidature candidature = candidatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
        return ResponseEntity.ok(CandidatureDetailResponse.from(candidature));
    }

    @GetMapping("/ma-candidature")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatureDetailResponse> maCandidature() {
        UUID userId = currentUserProvider.getCurrentUserId();
        var candidat = candidatRepository.findByUtilisateurId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil candidat pour cet utilisateur"));
        Candidature candidature = candidatureRepository
                .findByCandidatIdAndEditionId(candidat.getId(), candidat.getEdition().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidature introuvable"));
        return ResponseEntity.ok(CandidatureDetailResponse.from(candidature));
    }

    @PutMapping("/{id}/valider")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CandidatureDetailResponse> valider(@PathVariable UUID id) {
        var admin = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(CandidatureDetailResponse.from(candidatureService.valider(id, admin)));
    }

    @PutMapping("/{id}/rejeter")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<CandidatureDetailResponse> rejeter(@PathVariable UUID id,
                                                               @Valid @RequestBody RejeterCandidatureRequest request) {
        var admin = currentUserProvider.getCurrentUser();
        return ResponseEntity.ok(CandidatureDetailResponse.from(
                candidatureService.rejeter(id, admin, request.motifRejet())));
    }
}
