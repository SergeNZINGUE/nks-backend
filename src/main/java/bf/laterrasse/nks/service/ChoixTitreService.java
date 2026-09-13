package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.AffectationPoule;
import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.ChoixTitreCandidat;
import bf.laterrasse.nks.domain.Duo;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.SoireeEvent;
import bf.laterrasse.nks.domain.TitreImpose;
import bf.laterrasse.nks.domain.enums.Enums.StatutSoiree;
import bf.laterrasse.nks.dto.titre.ChoisirTitreRequest;
import bf.laterrasse.nks.dto.titre.ChoixTitreResponse;
import bf.laterrasse.nks.dto.titre.MonChoixTitreResponse;
import bf.laterrasse.nks.dto.titre.StatutChoixTitreCandidatResponse;
import bf.laterrasse.nks.dto.titre.TitreImposeResponse;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.AffectationPouleRepository;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.ChoixTitreCandidatRepository;
import bf.laterrasse.nks.repository.DuoRepository;
import bf.laterrasse.nks.repository.PhaseRepository;
import bf.laterrasse.nks.repository.SoireeEventRepository;
import bf.laterrasse.nks.repository.TitreImposeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Choix par chaque candidat, avant sa manche (= soirée, cf. vocabulaire déjà utilisé dans
 * la grille de délibération), d'un titre parmi la liste imposée par le CO pour la phase de
 * cette soirée, plus un titre personnel libre. Purement déclaratif côté délai : la date
 * limite éventuelle (Phase.dateLimiteChoixTitres) n'est jamais bloquante, seulement
 * utilisée par le rapport admin pour repérer les retardataires (cf. échange du 13/09/2026).
 */
@Service
@RequiredArgsConstructor
public class ChoixTitreService {

    private final CandidatRepository candidatRepository;
    private final TitreImposeRepository titreImposeRepository;
    private final ChoixTitreCandidatRepository choixTitreCandidatRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;
    private final SoireeEventRepository soireeEventRepository;
    private final PhaseRepository phaseRepository;

    @Transactional(readOnly = true)
    public MonChoixTitreResponse monChoixTitre(UUID utilisateurId) {
        Candidat candidat = candidatDuUtilisateur(utilisateurId);
        SoireeEvent soiree = resoudreSoireeActuelle(candidat).orElse(null);

        if (soiree == null) {
            return new MonChoixTitreResponse(null, null, null, null, null, List.of(), null);
        }

        Phase phase = soiree.getPhase();
        List<TitreImposeResponse> titres = titreImposeRepository.findByPhaseIdOrderByOrdreAsc(phase.getId()).stream()
                .map(TitreImposeResponse::from)
                .toList();
        ChoixTitreResponse choixActuel = choixTitreCandidatRepository
                .findByCandidatIdAndSoireeId(candidat.getId(), soiree.getId())
                .map(ChoixTitreResponse::from)
                .orElse(null);

        return new MonChoixTitreResponse(soiree.getId(), soiree.getNom(), soiree.getDateHeure(),
                phase.getNom().name(), phase.getDateLimiteChoixTitres(), titres, choixActuel);
    }

    @Transactional
    public ChoixTitreResponse choisir(UUID utilisateurId, ChoisirTitreRequest request) {
        Candidat candidat = candidatDuUtilisateur(utilisateurId);
        SoireeEvent soiree = resoudreSoireeActuelle(candidat)
                .orElseThrow(() -> new ValidationMetierException("Aucune soirée à venir pour laquelle choisir un titre"));

        TitreImpose titreImpose = titreImposeRepository.findById(request.titreImposeId())
                .orElseThrow(() -> new ResourceNotFoundException("Titre imposé introuvable"));
        if (!titreImpose.getPhase().getId().equals(soiree.getPhase().getId())) {
            throw new ValidationMetierException("Ce titre imposé n'appartient pas à la phase de ta soirée");
        }

        ChoixTitreCandidat choix = choixTitreCandidatRepository
                .findByCandidatIdAndSoireeId(candidat.getId(), soiree.getId())
                .orElseGet(() -> ChoixTitreCandidat.builder().candidat(candidat).soiree(soiree).build());
        choix.setTitreImpose(titreImpose);
        choix.setTitrePersonnel(request.titrePersonnel());
        choix.setDateChoix(Instant.now());

        return ChoixTitreResponse.from(choixTitreCandidatRepository.save(choix));
    }

    @Transactional(readOnly = true)
    public List<StatutChoixTitreCandidatResponse> statutPhase(UUID phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));
        Instant dateLimite = phase.getDateLimiteChoixTitres();

        List<SoireeEvent> soirees = soireeEventRepository.findByPhaseId(phaseId);
        List<StatutChoixTitreCandidatResponse> result = new java.util.ArrayList<>();

        for (SoireeEvent soiree : soirees) {
            List<AffectationPoule> viaPoule = affectationPouleRepository.findByPouleSoireeId(soiree.getId());
            List<Duo> viaDuo = duoRepository.findBySoireeId(soiree.getId());
            Set<Candidat> candidats = Stream.concat(
                    viaPoule.stream().map(AffectationPoule::getCandidat),
                    viaDuo.stream().flatMap(d -> Stream.of(d.getCandidat1(), d.getCandidat2()))
            ).collect(Collectors.toSet());

            for (Candidat candidat : candidats) {
                var choixOpt = choixTitreCandidatRepository.findByCandidatIdAndSoireeId(candidat.getId(), soiree.getId());
                boolean enRetard = dateLimite != null && Instant.now().isAfter(dateLimite) && choixOpt.isEmpty();
                result.add(new StatutChoixTitreCandidatResponse(
                        candidat.getId(), candidat.getCodeCandidat(),
                        (candidat.getUtilisateur().getPrenom() + " " + candidat.getUtilisateur().getNom()).trim(),
                        soiree.getId(), soiree.getNom(),
                        choixOpt.isPresent(),
                        choixOpt.map(c -> c.getTitreImpose().getTitre()).orElse(null),
                        choixOpt.map(ChoixTitreCandidat::getTitrePersonnel).orElse(null),
                        choixOpt.map(ChoixTitreCandidat::getDateChoix).orElse(null),
                        enRetard));
            }
        }
        return result;
    }

    private Candidat candidatDuUtilisateur(UUID utilisateurId) {
        return candidatRepository.findByUtilisateurId(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil candidat pour cet utilisateur"));
    }

    /** La soirée la plus proche (non terminée/annulée) parmi celles où le candidat est affecté, toutes phases confondues. */
    private java.util.Optional<SoireeEvent> resoudreSoireeActuelle(Candidat candidat) {
        List<AffectationPoule> viaPoule = affectationPouleRepository.findByCandidatId(candidat.getId());
        List<Duo> viaDuo = duoRepository.findByCandidatId(candidat.getId());

        return Stream.concat(
                        viaPoule.stream().map(a -> a.getPoule().getSoiree()),
                        viaDuo.stream().map(Duo::getSoiree))
                .filter(java.util.Objects::nonNull)
                .filter(s -> s.getStatut() != StatutSoiree.TERMINEE && s.getStatut() != StatutSoiree.ANNULEE)
                .min(Comparator.comparing(SoireeEvent::getDateHeure));
    }
}
