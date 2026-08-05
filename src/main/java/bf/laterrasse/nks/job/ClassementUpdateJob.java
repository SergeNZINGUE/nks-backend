package bf.laterrasse.nks.job;

import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import bf.laterrasse.nks.repository.EditionRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.service.ClassementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WF-07 : "Classement recalculé automatiquement toutes les heures pendant les phases de
 * vote actives." Le calcul final officiel reste déclenché manuellement par l'admin
 * (ClassementController.publier) après clôture des votes et notes jury.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClassementUpdateJob {

    private final EditionRepository editionRepository;
    private final PhaseRepository phaseRepository;
    private final ClassementService classementService;

    @Scheduled(cron = "0 0 * * * *") // toutes les heures pile
    public void recalculer() {
        editionRepository.findByStatut(StatutEdition.EN_COURS).ifPresent(this::recalculerPourEdition);
    }

    private void recalculerPourEdition(Edition edition) {
        List<Phase> phasesActives = phaseRepository.findByEditionIdOrderByOrdreAsc(edition.getId())
                .stream().filter(Phase::isVoteActif).toList();

        for (Phase phase : phasesActives) {
            try {
                classementService.calculerClassementPhase(phase.getId());
            } catch (Exception e) {
                log.error("Échec recalcul classement phase {} : {}", phase.getId(), e.getMessage());
            }
        }
        if (!phasesActives.isEmpty()) {
            classementService.mettreAJourClassementGlobal(edition.getId());
        }
    }
}
