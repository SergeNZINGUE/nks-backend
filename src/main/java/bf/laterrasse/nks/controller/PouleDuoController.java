package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Poule;
import bf.laterrasse.nks.dto.admin.AffecterPouleRequest;
import bf.laterrasse.nks.dto.admin.RepechageRequest;
import bf.laterrasse.nks.dto.poule.AffectationPouleResponse;
import bf.laterrasse.nks.dto.poule.PouleResponse;
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
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional
    public ResponseEntity<PouleResponse> creerPoule(@RequestBody Map<String, Object> body) {
        UUID phaseId = UUID.fromString((String) body.get("phaseId"));
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
        Poule poule = Poule.builder().phase(phase).nom((String) body.get("nom")).build();
        return ResponseEntity.status(201).body(PouleResponse.from(pouleRepository.save(poule)));
    }

    @GetMapping("/poules/phase/{phaseId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PouleResponse>> poulesPhase(@PathVariable UUID phaseId) {
        return ResponseEntity.ok(
                pouleRepository.findByPhaseIdWithDetails(phaseId).stream()
                        .map(PouleResponse::from)
                        .toList()
        );
    }

    @PostMapping("/poules/{id}/affecter")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<List<AffectationPouleResponse>> affecter(@PathVariable UUID id,
                                                                     @Valid @RequestBody AffecterPouleRequest request) {
        return ResponseEntity.ok(
                competitionAdminService.affecterCandidats(id, request.candidatIds()).stream()
                        .map(AffectationPouleResponse::from)
                        .toList()
        );
    }

    @GetMapping("/poules/{id}/candidats")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AffectationPouleResponse>> candidats(@PathVariable UUID id) {
        return ResponseEntity.ok(
                affectationPouleRepository.findByPouleIdWithDetails(id).stream()
                        .map(AffectationPouleResponse::from)
                        .toList()
        );
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

    @PutMapping("/poules/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<PouleResponse> renommerPoule(@PathVariable UUID id,
                                                        @RequestBody Map<String, Object> body) {
        Poule poule = pouleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Poule introuvable"));
        if (body.get("nom") instanceof String nom) poule.setNom(nom);
        return ResponseEntity.ok(PouleResponse.from(pouleRepository.save(poule)));
    }

    @PutMapping("/affectations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<AffectationPouleResponse> mettreAJourAffectation(@PathVariable UUID id,
                                                                             @RequestBody Map<String, Object> body) {
        AffectationPoule a = affectationPouleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Affectation introuvable"));
        if (body.get("ordrePassage") instanceof Number n) a.setOrdrePassage(n.shortValue());
        if (body.containsKey("chansonImposee")) a.setChansonImposee((String) body.get("chansonImposee"));
        affectationPouleRepository.save(a);
        return ResponseEntity.ok(AffectationPouleResponse.from(a));
    }

    @DeleteMapping("/affectations/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<Void> retirerAffectation(@PathVariable UUID id) {
        if (!affectationPouleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Affectation introuvable");
        }
        affectationPouleRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/candidats/{id}/repechage")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> repecher(@PathVariable UUID id, @RequestParam UUID phaseId,
                                       @Valid @RequestBody RepechageRequest request) {
        return ResponseEntity.ok(competitionAdminService.repecher(id, phaseId, request.motif()));
    }
}
