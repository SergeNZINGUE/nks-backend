package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.enums.Enums.StatutSoiree;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.service.BilletterieService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** §13.10. */
@RestController
@RequestMapping("/soirees")
@RequiredArgsConstructor
public class SoireeController {

    private final SoireeEventRepository soireeEventRepository;
    private final PhaseRepository phaseRepository;
    private final BilletterieService billetterieService;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<SoireeEvent>> lister(@RequestParam(required = false) UUID editionId) {
        if (editionId != null) {
            return ResponseEntity.ok(soireeEventRepository.findByEditionId(editionId));
        }
        return ResponseEntity.ok(soireeEventRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    @Transactional
    public ResponseEntity<SoireeEvent> creer(@RequestBody SoireeEvent soiree, @RequestParam UUID phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
        soiree.setId(null);
        soiree.setPhase(phase);
        soiree.setEdition(phase.getEdition());
        return ResponseEntity.status(201).body(soireeEventRepository.save(soiree));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<SoireeEvent> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(soireeEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable")));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','ORGANISATEUR')")
    @Transactional
    public ResponseEntity<SoireeEvent> mettreAJour(@PathVariable UUID id, @RequestBody SoireeEvent modif) {
        SoireeEvent soiree = soireeEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable"));
        StatutSoiree ancienStatut = soiree.getStatut();
        soiree.setNom(modif.getNom());
        soiree.setDateHeure(modif.getDateHeure());
        soiree.setLieu(modif.getLieu());
        soiree.setAdresse(modif.getAdresse());
        soiree.setCapaciteMax(modif.getCapaciteMax());
        soiree.setStatut(modif.getStatut());
        soiree.setVoteSurPlaceActif(modif.isVoteSurPlaceActif());
        SoireeEvent sauvegardee = soireeEventRepository.save(soiree);

        // Clôture de soirée : les billets EMIS jamais scannés expirent (demande client). On ne
        // déclenche la cascade que sur la transition vers TERMINEE, jamais si elle l'était déjà,
        // pour éviter de re-parcourir les tickets à chaque mise à jour ultérieure.
        if (sauvegardee.getStatut() == StatutSoiree.TERMINEE && ancienStatut != StatutSoiree.TERMINEE) {
            billetterieService.expirerTicketsSoiree(sauvegardee.getId());
        }

        return ResponseEntity.ok(sauvegardee);
    }
}
