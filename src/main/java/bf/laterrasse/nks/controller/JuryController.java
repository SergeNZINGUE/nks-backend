package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Jury;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;
import bf.laterrasse.nks.dto.jury.CritereNotationResponse;
import bf.laterrasse.nks.dto.jury.NoteJuryResponse;
import bf.laterrasse.nks.dto.soiree.SoireeEventResponse;
import bf.laterrasse.nks.dto.jury.SaisirNotesRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CritereNotationRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.JuryRepository;
import bf.laterrasse.nks.repository.NoteJuryRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.JuryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** §13.8 — WF-06. */
@RestController
@RequiredArgsConstructor
public class JuryController {

    private final JuryService juryService;
    private final JuryRepository juryRepository;
    private final NoteJuryRepository noteJuryRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final CritereNotationRepository critereNotationRepository;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/jury/soirees")
    @PreAuthorize("hasRole('JURY')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<SoireeEventResponse>> mesSoirees() {
        Jury jury = juryDuUtilisateurCourant();
        List<SoireeEventResponse> soirees = jury.getSoirees().stream()
                .map(SoireeEventResponse::from)
                .toList();
        return ResponseEntity.ok(soirees);
    }

    @GetMapping("/jury/candidats")
    @PreAuthorize("hasRole('JURY')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CandidatPublicResponse>> candidatsANoter(@RequestParam UUID soireeId) {
        List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soireeId);
        List<Duo> viaDuo = duoRepository.findBySoireeId(soireeId);

        Set<Candidat> candidats = Stream.concat(
                viaPoule.stream().map(AffectationPoule::getCandidat),
                viaDuo.stream().flatMap(d -> Stream.of(d.getCandidat1(), d.getCandidat2()))
        ).collect(java.util.stream.Collectors.toSet());

        List<CandidatPublicResponse> result = candidats.stream()
                .map(CandidatPublicResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/jury/notes")
    @PreAuthorize("hasRole('JURY')")
    @Transactional
    public ResponseEntity<List<NoteJuryResponse>> saisirNotes(@Valid @RequestBody SaisirNotesRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        List<NoteJuryResponse> result = juryService.saisirNotes(utilisateurId, request).stream()
                .map(NoteJuryResponse::from)
                .toList();
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/jury/mes-notes")
    @PreAuthorize("hasRole('JURY')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<NoteJuryResponse>> mesNotes(@RequestParam UUID soireeId) {
        Jury jury = juryDuUtilisateurCourant();
        List<NoteJuryResponse> result = noteJuryRepository.findByJuryIdAndSoireeId(jury.getId(), soireeId).stream()
                .map(NoteJuryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jury/notes/soiree/{soireeId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<NoteJuryResponse>> notesSoiree(@PathVariable UUID soireeId) {
        List<NoteJuryResponse> result = noteJuryRepository.findBySoireeId(soireeId).stream()
                .map(NoteJuryResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/jury/criteres")
    @PreAuthorize("hasRole('JURY')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CritereNotationResponse>> criteres(@RequestParam UUID soireeId) {
        List<CritereNotationResponse> result = critereNotationRepository.findActiveBySoireeId(soireeId).stream()
                .map(CritereNotationResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/soirees/{id}/cloturer-notation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> cloturerNotation(@PathVariable UUID id) {
        juryService.cloturerSoiree(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/soirees/{id}/deverrouiller-notation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> deverrouillerNotation(@PathVariable UUID id) {
        juryService.deverrouillerSoiree(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/soirees/{id}/publier-resultats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> publierResultats(@PathVariable UUID id) {
        juryService.publierResultats(id);
        return ResponseEntity.noContent().build();
    }

    private Jury juryDuUtilisateurCourant() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return juryRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil jury pour cet utilisateur"));
    }
}
