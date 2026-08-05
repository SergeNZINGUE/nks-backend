package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Poule;
import bf.laterrasse.nks.dto.admin.AffecterPouleRequest;
import bf.laterrasse.nks.dto.admin.RepechageRequest;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.PouleRepository;
import bf.laterrasse.nks.service.CompetitionAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** §13.10, §13.9 — US-25/26/27. */
@RestController
@RequiredArgsConstructor
public class PouleDuoController {

    private final CompetitionAdminService competitionAdminService;
    private final PouleRepository pouleRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final PhaseRepository phaseRepository;

    @PostMapping("/poules")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Poule> creerPoule(@RequestBody Map<String, Object> body) {
        UUID phaseId = UUID.fromString((String) body.get("phaseId"));
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
        Poule poule = Poule.builder().phase(phase).nom((String) body.get("nom")).build();
        return ResponseEntity.status(201).body(pouleRepository.save(poule));
    }

    @PostMapping("/poules/{id}/affecter")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<AffectationPoule>> affecter(@PathVariable UUID id,
                                                             @Valid @RequestBody AffecterPouleRequest request) {
        return ResponseEntity.ok(competitionAdminService.affecterCandidats(id, request.candidatIds()));
    }

    @GetMapping("/poules/{id}/candidats")
    public ResponseEntity<List<AffectationPoule>> candidats(@PathVariable UUID id) {
        return ResponseEntity.ok(affectationPouleRepository.findByPouleId(id));
    }

    @PostMapping("/duos")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Duo> creerDuo(@RequestBody Map<String, Object> body) {
        UUID phaseId = UUID.fromString((String) body.get("phaseId"));
        UUID candidat1Id = UUID.fromString((String) body.get("candidat1Id"));
        UUID candidat2Id = UUID.fromString((String) body.get("candidat2Id"));
        String chanson = (String) body.get("chansonCommune");
        UUID soireeId = body.get("soireeId") != null ? UUID.fromString((String) body.get("soireeId")) : null;
        return ResponseEntity.status(201).body(
                competitionAdminService.creerDuo(phaseId, candidat1Id, candidat2Id, chanson, soireeId));
    }

    @GetMapping("/duos/phase/{phaseId}")
    public ResponseEntity<List<Duo>> duosPhase(@PathVariable UUID phaseId) {
        return ResponseEntity.ok(duoRepository.findByPhaseId(phaseId));
    }

    @PostMapping("/candidats/{id}/repechage")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> repecher(@PathVariable UUID id, @RequestParam UUID phaseId,
                                       @Valid @RequestBody RepechageRequest request) {
        return ResponseEntity.ok(competitionAdminService.repecher(id, phaseId, request.motif()));
    }
}
