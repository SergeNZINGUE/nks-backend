package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Jury;
import bf.laterrasse.nks.domain.NoteJury;
import bf.laterrasse.nks.dto.jury.SaisirNotesRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.JuryRepository;
import bf.laterrasse.nks.repository.NoteJuryRepository;
import bf.laterrasse.nks.security.CurrentUserProvider;
import bf.laterrasse.nks.service.JuryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CurrentUserProvider currentUserProvider;

    @GetMapping("/jury/soirees")
    @PreAuthorize("hasRole('JURY')")
    public ResponseEntity<?> mesSoirees() {
        Jury jury = juryDuUtilisateurCourant();
        return ResponseEntity.ok(jury.getSoirees());
    }

    @GetMapping("/jury/candidats")
    @PreAuthorize("hasRole('JURY')")
    public ResponseEntity<?> candidatsANoter(@RequestParam UUID soireeId) {
        List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soireeId);
        List<Duo> viaDuo = duoRepository.findBySoireeId(soireeId);

        Set<bf.laterrasse.nks.domain.Candidat> candidats = Stream.concat(
                viaPoule.stream().map(AffectationPoule::getCandidat),
                viaDuo.stream().flatMap(d -> Stream.of(d.getCandidat1(), d.getCandidat2()))
        ).collect(java.util.stream.Collectors.toSet());

        return ResponseEntity.ok(candidats);
    }

    @PostMapping("/jury/notes")
    @PreAuthorize("hasRole('JURY')")
    public ResponseEntity<List<NoteJury>> saisirNotes(@Valid @RequestBody SaisirNotesRequest request) {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return ResponseEntity.status(201).body(juryService.saisirNotes(utilisateurId, request));
    }

    @GetMapping("/jury/mes-notes")
    @PreAuthorize("hasRole('JURY')")
    public ResponseEntity<List<NoteJury>> mesNotes(@RequestParam UUID soireeId) {
        Jury jury = juryDuUtilisateurCourant();
        return ResponseEntity.ok(noteJuryRepository.findByJuryIdAndSoireeId(jury.getId(), soireeId));
    }

    @GetMapping("/jury/notes/soiree/{soireeId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<NoteJury>> notesSoiree(@PathVariable UUID soireeId) {
        return ResponseEntity.ok(noteJuryRepository.findBySoireeId(soireeId));
    }

    @PutMapping("/soirees/{id}/cloturer-notation")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> cloturerNotation(@PathVariable UUID id) {
        juryService.cloturerSoiree(id);
        return ResponseEntity.noContent().build();
    }

    private Jury juryDuUtilisateurCourant() {
        UUID utilisateurId = currentUserProvider.getCurrentUserId();
        return juryRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil jury pour cet utilisateur"));
    }
}
