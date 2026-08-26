package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Classement;
import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import bf.laterrasse.nks.dto.classement.ClassementResponse;
import bf.laterrasse.nks.dto.classement.ResultatPhaseResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.ClassementRepository;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.ResultatPhaseRepository;
import bf.laterrasse.nks.service.ClassementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional(readOnly = true)
    public ResponseEntity<List<ClassementResponse>> classementEditionEnCours() {
        var edition = editionRepository.findByStatut(StatutEdition.EN_COURS)
                .orElseThrow(() -> new ResourceNotFoundException("Aucune édition en cours"));
        List<ClassementResponse> result = classementRepository
                .findByEditionIdOrderByRangGlobalAsc(edition.getId()).stream()
                .map(ClassementResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/classement/phase/{phaseId}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ResultatPhaseResponse>> classementPhase(@PathVariable UUID phaseId) {
        List<ResultatPhaseResponse> result = resultatPhaseRepository.findByPhaseIdOrderByRangAsc(phaseId).stream()
                .map(ResultatPhaseResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/phases/{id}/calculer-classement")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional
    public ResponseEntity<List<ResultatPhaseResponse>> calculer(@PathVariable UUID id) {
        List<ResultatPhaseResponse> result = classementService.calculerClassementPhase(id).stream()
                .map(ResultatPhaseResponse::from)
                .toList();
        return ResponseEntity.ok(result);
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
