package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.enums.Enums.StatutPhase;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.repository.PhaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** §13.9, §13.6 (fenêtres de vote — US-18). */
@RestController
@RequestMapping("/phases")
@RequiredArgsConstructor
public class PhaseController {

    private final PhaseRepository phaseRepository;

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> mettreAJour(@PathVariable UUID id, @RequestBody Phase modif) {
        Phase phase = getPhase(id);
        phase.setDateDebut(modif.getDateDebut());
        phase.setDateFin(modif.getDateFin());
        phase.setPoidsVotesEnLigne(modif.getPoidsVotesEnLigne());
        phase.setPoidsPublicSurPlace(modif.getPoidsPublicSurPlace());
        phase.setPoidsJury(modif.getPoidsJury());
        phase.setPointsMaxVotesEnLigne(modif.getPointsMaxVotesEnLigne());
        phase.setPointsMaxPublic(modif.getPointsMaxPublic());
        phase.setPointsMaxJury(modif.getPointsMaxJury());
        phase.setJuryObligatoire(modif.isJuryObligatoire());
        return ResponseEntity.ok(phaseRepository.save(phase));
    }

    @PutMapping("/{id}/activer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> activer(@PathVariable UUID id) {
        Phase phase = getPhase(id);
        phase.setStatut(StatutPhase.EN_COURS);
        return ResponseEntity.ok(phaseRepository.save(phase));
    }

    @PutMapping("/{id}/cloturer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> cloturer(@PathVariable UUID id) {
        Phase phase = getPhase(id);
        phase.setStatut(StatutPhase.TERMINEE);
        phase.setVoteActif(false);
        return ResponseEntity.ok(phaseRepository.save(phase));
    }

    @PutMapping("/{id}/vote/activer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> activerVote(@PathVariable UUID id) {
        Phase phase = getPhase(id);
        phase.setVoteActif(true);
        phase.setDateOuvertureVote(Instant.now());
        return ResponseEntity.ok(phaseRepository.save(phase));
    }

    @PutMapping("/{id}/vote/desactiver")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<Phase> desactiverVote(@PathVariable UUID id) {
        Phase phase = getPhase(id);
        phase.setVoteActif(false);
        phase.setDateFermetureVote(Instant.now());
        return ResponseEntity.ok(phaseRepository.save(phase));
    }

    private Phase getPhase(UUID id) {
        return phaseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
    }
}
