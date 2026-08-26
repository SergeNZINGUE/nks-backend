package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;
import bf.laterrasse.nks.dto.candidat.MettreAJourProfilRequest;
import bf.laterrasse.nks.dto.classement.ResultatPhaseResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.ResultatPhaseRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
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

/** §13.5 — Galerie publique et profils candidats (US-12, US-15, US-16). */
@RestController
@RequestMapping("/candidats")
@RequiredArgsConstructor
public class CandidatController {

    private final CandidatRepository candidatRepository;
    private final ResultatPhaseRepository resultatPhaseRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<CandidatPublicResponse>> galerie(
            @RequestParam UUID editionId,
            @RequestParam(required = false) StatutProfilCandidat statutProfil,
            Pageable pageable) {
        StatutProfilCandidat filtre = statutProfil != null ? statutProfil : StatutProfilCandidat.ACTIF;
        Page<CandidatPublicResponse> page = candidatRepository
                .findByEditionIdAndStatutProfil(editionId, filtre, pageable)
                .map(CandidatPublicResponse::from);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatPublicResponse> profil(@PathVariable UUID id) {
        return ResponseEntity.ok(CandidatPublicResponse.from(getCandidat(id)));
    }

    @GetMapping("/code/{code}")
    @Transactional(readOnly = true)
    public ResponseEntity<CandidatPublicResponse> parCode(@PathVariable String code, @RequestParam UUID editionId) {
        Candidat candidat = candidatRepository.findByEditionIdAndCodeCandidat(editionId, code)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable pour le code " + code));
        return ResponseEntity.ok(CandidatPublicResponse.from(candidat));
    }

    @GetMapping("/{id}/scores")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ResultatPhaseResponse>> scores(@PathVariable UUID id) {
        getCandidat(id); // 404 si le candidat n'existe pas
        List<ResultatPhaseResponse> result = resultatPhaseRepository.findByCandidatId(id).stream()
                .map(ResultatPhaseResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/mon-profil")
    @PreAuthorize("hasRole('CANDIDAT')")
    @Transactional
    public ResponseEntity<CandidatPublicResponse> mettreAJourMonProfil(@Valid @RequestBody MettreAJourProfilRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        Candidat candidat = candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil candidat pour cet utilisateur"));
        if (request.biographie() != null) {
            candidat.setBiographie(request.biographie());
        }
        return ResponseEntity.ok(CandidatPublicResponse.from(candidatRepository.save(candidat)));
    }

    private Candidat getCandidat(UUID id) {
        return candidatRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
    }
}
