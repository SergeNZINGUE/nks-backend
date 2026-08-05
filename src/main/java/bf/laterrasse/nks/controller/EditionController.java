package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.9. */
@RestController
@RequestMapping("/editions")
@RequiredArgsConstructor
public class EditionController {

    private final EditionRepository editionRepository;
    private final PhaseRepository phaseRepository;

    @GetMapping
    public ResponseEntity<List<Edition>> lister() {
        return ResponseEntity.ok(editionRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Edition> creer(@RequestBody Edition edition) {
        edition.setId(null);
        return ResponseEntity.status(201).body(editionRepository.save(edition));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Edition> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(editionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Edition> mettreAJour(@PathVariable UUID id, @RequestBody Edition modif) {
        Edition edition = editionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable"));
        edition.setNom(modif.getNom());
        edition.setAnnee(modif.getAnnee());
        edition.setStatut(modif.getStatut());
        edition.setDateDebutInscriptions(modif.getDateDebutInscriptions());
        edition.setDateFinInscriptions(modif.getDateFinInscriptions());
        edition.setDateDebutCompetition(modif.getDateDebutCompetition());
        edition.setDateFinCompetition(modif.getDateFinCompetition());
        edition.setDescription(modif.getDescription());
        edition.setDateModification(java.time.Instant.now());
        return ResponseEntity.ok(editionRepository.save(edition));
    }

    @GetMapping("/{id}/phases")
    public ResponseEntity<List<Phase>> phases(@PathVariable UUID id) {
        return ResponseEntity.ok(phaseRepository.findByEditionIdOrderByOrdreAsc(id));
    }

    @PostMapping("/{id}/phases")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> creerPhase(@PathVariable UUID id, @RequestBody Phase phase) {
        Edition edition = editionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Édition introuvable"));
        if (phase.getPoidsVotesEnLigne() + phase.getPoidsPublicSurPlace() + phase.getPoidsJury() != 100) {
            throw new bf.laterrasse.nks.exception.ValidationMetierException(
                    "Les pondérations votes en ligne + public + jury doivent totaliser 100%");
        }
        phase.setId(null);
        phase.setEdition(edition);
        return ResponseEntity.status(201).body(phaseRepository.save(phase));
    }
}
