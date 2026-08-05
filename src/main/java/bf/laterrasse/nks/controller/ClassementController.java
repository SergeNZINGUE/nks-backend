package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Classement;
import bf.laterrasse.nks.domain.ResultatPhase;
import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.ClassementRepository;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.ResultatPhaseRepository;
import bf.laterrasse.nks.service.ClassementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.9, §13.14 — WF-07. */
@RestController
@RequiredArgsConstructor
public class ClassementController {

    private final ClassementService classementService;
    private final ClassementRepository classementRepository;
    private final ResultatPhaseRepository resultatPhaseRepository;
    private final EditionRepository editionRepository;

    @GetMapping("/classement")
    public ResponseEntity<List<Classement>> classementEditionEnCours() {
        var edition = editionRepository.findByStatut(StatutEdition.EN_COURS)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune édition en cours"));
        return ResponseEntity.ok(classementRepository.findByEditionIdOrderByRangGlobalAsc(edition.getId()));
    }

    @GetMapping("/classement/phase/{phaseId}")
    public ResponseEntity<List<ResultatPhase>> classementPhase(@PathVariable UUID phaseId) {
        return ResponseEntity.ok(resultatPhaseRepository.findByPhaseIdOrderByRangAsc(phaseId));
    }

    @PostMapping("/phases/{id}/calculer-classement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<List<ResultatPhase>> calculer(@PathVariable UUID id) {
        return ResponseEntity.ok(classementService.calculerClassementPhase(id));
    }

    @PostMapping("/classement/publier")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Void> publier(@RequestParam UUID editionId) {
        classementService.mettreAJourClassementGlobal(editionId);
        List<Classement> classements = classementRepository.findByEditionIdOrderByRangGlobalAsc(editionId);
        classements.forEach(c -> c.setOfficiel(true));
        classementRepository.saveAll(classements);
        return ResponseEntity.noContent().build();
    }
}
