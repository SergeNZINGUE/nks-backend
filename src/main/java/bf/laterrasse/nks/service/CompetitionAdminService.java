package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.StatutQualification;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** US-25/26/27 — Poules, Duos, Repêchage (WF-08). */
@Service
@RequiredArgsConstructor
public class CompetitionAdminService {

    private final PouleRepository pouleRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final CandidatRepository candidatRepository;
    private final DuoRepository duoRepository;
    private final PhaseRepository phaseRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final ResultatPhaseRepository resultatPhaseRepository;
    private final NotificationService notificationService;

    @Transactional
    public List<AffectationPoule> affecterCandidats(UUID pouleId, List<UUID> candidatIds) {
        Poule poule = pouleRepository.findById(pouleId)
                .orElseThrow(() -> new ResourceNotFoundException("Poule introuvable"));

        return candidatIds.stream().map(candidatId -> {
            Candidat candidat = candidatRepository.findById(candidatId)
                    .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable : " + candidatId));
            if (affectationPouleRepository.existsByCandidatIdAndPhaseId(candidatId, poule.getPhase().getId())) {
                throw new ConflitEtatException(
                        "Le candidat " + candidat.getCodeCandidat() + " est déjà affecté à une poule de cette phase (RM-41)");
            }
            return affectationPouleRepository.save(AffectationPoule.builder()
                    .candidat(candidat).poule(poule).build());
        }).toList();
    }

    @Transactional
    public Duo creerDuo(UUID phaseId, UUID candidat1Id, UUID candidat2Id, String chansonCommune, UUID soireeId) {
        if (candidat1Id.equals(candidat2Id)) {
            throw new ValidationMetierException("Un duo doit être composé de deux candidats distincts");
        }
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
        SoireeEvent soiree = soireeId != null ? soireeEventRepository.findById(soireeId)
                .orElseThrow(() -> new ResourceNotFoundException("Soirée introuvable")) : null;
        Candidat c1 = candidatRepository.findById(candidat1Id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat 1 introuvable"));
        Candidat c2 = candidatRepository.findById(candidat2Id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat 2 introuvable"));

        boolean dejaEnDuo = duoRepository.findByPhaseId(phaseId).stream()
                .anyMatch(d -> d.getCandidat1().getId().equals(candidat1Id) || d.getCandidat2().getId().equals(candidat1Id)
                        || d.getCandidat1().getId().equals(candidat2Id) || d.getCandidat2().getId().equals(candidat2Id));
        if (dejaEnDuo) {
            throw new ConflitEtatException("Un des deux candidats est déjà affecté à un duo pour cette phase");
        }

        return duoRepository.save(Duo.builder()
                .phase(phase).soiree(soiree)
                .candidat1(c1).candidat2(c2)
                .chansonCommune(chansonCommune)
                .build());
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "CANDIDAT_REPECHE", entite = "ResultatPhase")
    public ResultatPhase repecher(UUID candidatId, UUID phaseId, String motif) {
        Candidat candidat = candidatRepository.findById(candidatId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));

        ResultatPhase resultat = resultatPhaseRepository.findByCandidatIdAndPhaseId(candidatId, phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun résultat de phase pour ce candidat — calculez d'abord le classement"));

        resultat.setStatutQualification(StatutQualification.REPECHAGE);
        resultat.setMotifRepechage(motif);
        resultatPhaseRepository.save(resultat);

        Utilisateur utilisateur = candidat.getUtilisateur();
        notificationService.envoyerSmsEtEmail(utilisateur, utilisateur.getTelephone(), utilisateur.getEmail(),
                TypeNotification.REPECHAGE,
                "NKS : bonne nouvelle, vous avez été repêché(e) pour la phase suivante !",
                "NKS — Repêchage",
                "<p>Le comité NKS a décidé de vous repêcher pour la phase suivante.</p><p><strong>Motif :</strong> " + motif + "</p>");

        return resultat;
    }
}
