package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.NomPhase;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutQualification;
import bf.laterrasse.nks.event.ClassementRefreshEvent;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * WF-07 — Calcul du classement par phase. Interprétation retenue pour l'agrégation des
 * votes sociaux (documentée car le cahier des charges est ambigu à ce sujet, cf. rapport
 * §RM-19/20/21 et WF-05) :
 *   - Votes payants  : ratio (voix_candidat / total_voix_phase) × points_max_votes_en_ligne
 *                      — CUMULATIF sur toute la phase (peut couvrir plusieurs soirées).
 *   - Votes sociaux  : likes × 0,25 + commentaires × 0,75, ADDITIONNÉS aux points votes
 *                      payants dans la même enveloppe "votes en ligne", plafonnés à
 *                      points_max_votes_en_ligne (pour ne jamais dépasser le poids alloué
 *                      à la phase). Cette règle est configurable/à valider avec le client
 *                      si l'intention réelle diffère.
 *   - Vote sur place : ratio (voix_sur_place_candidat / total_voix_sur_place_de_SA_SOIRÉE) ×
 *                      points_max_public — SPÉCIFIQUE À LA SOIRÉE où le candidat s'est produit
 *                      (résolue via sa poule/son duo, cf. resoudreSoireeId), PAS cumulatif sur
 *                      toute la phase : un candidat ne doit pas être dilué par les votes d'une
 *                      autre soirée de la même phase à laquelle il n'a pas participé. Un
 *                      candidat sans poule/duo affecté à une soirée obtient 0 point public
 *                      (soirée indéterminée).
 *   - Jury           : (moyenne des totaux /100 par juré) × points_max_jury — jury
 *                      obligatoire en finale (H7, décision client), donc jamais 0 par
 *                      absence de jury sur cette phase.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClassementService {

    private static final BigDecimal POIDS_LIKE = new BigDecimal("0.25");
    private static final BigDecimal POIDS_COMMENTAIRE = new BigDecimal("0.75");

    private final PhaseRepository phaseRepository;
    private final CandidatRepository candidatRepository;
    private final VoteRepository voteRepository;
    private final VoteService voteService;
    private final NoteJuryRepository noteJuryRepository;
    private final ResultatPhaseRepository resultatPhaseRepository;
    private final ClassementRepository classementRepository;
    private final AffectationPouleRepository affectationPouleRepository;
    private final DuoRepository duoRepository;

    @Transactional
    public List<ResultatPhase> calculerClassementPhase(UUID phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new IllegalArgumentException("Phase introuvable : " + phaseId));

        List<Candidat> candidats = candidatRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(phase.getEdition().getId()))
                .filter(c -> c.getStatutProfil() != StatutProfilCandidat.SUSPENDU
                        && c.getStatutProfil() != StatutProfilCandidat.EN_ATTENTE)
                .toList();

        long totalVoixPayantes = voteService.totalVotesPayantsConfirmes(phaseId);

        // Vote public sur place : total à l'échelle de la soirée de CHAQUE candidat, pas de la
        // phase entière. On résout d'abord la soirée de chacun (via poule/duo), puis on
        // additionne les voix des candidats partageant la même soirée.
        Map<UUID, UUID> soireeParCandidat = new HashMap<>();
        for (Candidat c : candidats) {
            UUID soireeId = resoudreSoireeId(c, phase);
            if (soireeId != null) soireeParCandidat.put(c.getId(), soireeId);
        }
        Map<UUID, Long> totalSurPlaceParSoiree = new HashMap<>();
        for (Candidat c : candidats) {
            UUID soireeId = soireeParCandidat.get(c.getId());
            if (soireeId == null) continue;
            totalSurPlaceParSoiree.merge(soireeId, voteService.votesSurPlace(c.getId(), phase.getId()), Long::sum);
        }

        List<ResultatPhase> resultats = candidats.stream()
                .map(candidat -> {
                    UUID soireeId = soireeParCandidat.get(candidat.getId());
                    long totalVoixSurPlaceSoiree = soireeId != null ? totalSurPlaceParSoiree.getOrDefault(soireeId, 0L) : 0L;
                    return calculerPourCandidat(candidat, phase, totalVoixPayantes, totalVoixSurPlaceSoiree);
                })
                .sorted(Comparator.comparing(ResultatPhase::getTotalPoints).reversed())
                .toList();

        int rang = 1;
        for (ResultatPhase r : resultats) {
            r.setRang(rang++);
        }

        if (phase.getNom() == NomPhase.ELIMINATOIRES) {
            // Top 2 par poule → QUALIFIE, reste → ELIMINE (WF-08).
            appliquerQualificationParPoule(resultats, phase);
        } else {
            for (ResultatPhase r : resultats) {
                if (r.getStatutQualification() == StatutQualification.EN_ATTENTE) {
                    r.setStatutQualification(StatutQualification.QUALIFIE);
                }
            }
        }

        resultatPhaseRepository.saveAll(resultats);

        log.info("Classement recalculé pour la phase {} ({} candidats)", phaseId, resultats.size());
        return resultats;
    }

    /** Poule ou duo du candidat pour cette phase → soirée où il s'est produit. Null si non affecté. */
    private UUID resoudreSoireeId(Candidat candidat, Phase phase) {
        var viaPoule = affectationPouleRepository.findByCandidatIdAndPoulePhaseId(candidat.getId(), phase.getId())
                .map(a -> a.getPoule().getSoiree());
        if (viaPoule.isPresent() && viaPoule.get() != null) {
            return viaPoule.get().getId();
        }
        return duoRepository.findByPhaseIdAndCandidatId(phase.getId(), candidat.getId())
                .map(Duo::getSoiree)
                .filter(java.util.Objects::nonNull)
                .map(SoireeEvent::getId)
                .orElse(null);
    }

    private ResultatPhase calculerPourCandidat(Candidat candidat, Phase phase, long totalVoixPayantes, long totalVoixSurPlaceSoiree) {
        BigDecimal pointsVotesEnLigne = calculerPointsVotesEnLigne(candidat, phase, totalVoixPayantes);
        BigDecimal pointsPublic = calculerPointsRatio(
                voteService.votesSurPlace(candidat.getId(), phase.getId()), totalVoixSurPlaceSoiree, phase.getPointsMaxPublic());
        BigDecimal pointsJury = calculerPointsJury(candidat, phase);

        BigDecimal total = pointsVotesEnLigne.add(pointsPublic).add(pointsJury);

        ResultatPhase resultat = resultatPhaseRepository.findByCandidatIdAndPhaseId(candidat.getId(), phase.getId())
                .orElse(ResultatPhase.builder().candidat(candidat).phase(phase).build());
        resultat.setPointsVotesEnLigne(pointsVotesEnLigne);
        resultat.setPointsPublicSurPlace(pointsPublic);
        resultat.setPointsJury(pointsJury);
        resultat.setTotalPoints(total);
        resultat.setDateCalcul(Instant.now());
        return resultat;
    }

    /**
     * Pour la phase ELIMINATOIRES : les 2 premiers de chaque poule sont QUALIFIÉS,
     * les suivants sont ÉLIMINÉS. Un candidat sans poule est ÉLIMINÉ.
     * Le statut REPECHAGE déjà positionné par un admin n'est jamais écrasé.
     * Le passage à StatutProfilCandidat.ELIMINE est différé à la clôture officielle
     * de la soirée (TERMINEE) via {@link #appliquerEliminationsStatutProfil}.
     */
    private void appliquerQualificationParPoule(List<ResultatPhase> resultats, Phase phase) {
        Map<UUID, UUID> pouleParCandidat = new HashMap<>();
        for (ResultatPhase r : resultats) {
            UUID cid = r.getCandidat().getId();
            affectationPouleRepository.findByCandidatIdAndPoulePhaseId(cid, phase.getId())
                    .ifPresent(a -> pouleParCandidat.put(cid, a.getPoule().getId()));
        }

        Map<UUID, List<ResultatPhase>> parPoule = resultats.stream()
                .filter(r -> pouleParCandidat.containsKey(r.getCandidat().getId()))
                .collect(Collectors.groupingBy(r -> pouleParCandidat.get(r.getCandidat().getId())));

        int nbElimines = 0;
        for (List<ResultatPhase> groupe : parPoule.values()) {
            List<ResultatPhase> triés = groupe.stream()
                    .sorted(Comparator.comparing(ResultatPhase::getTotalPoints).reversed())
                    .toList();
            for (int i = 0; i < triés.size(); i++) {
                ResultatPhase r = triés.get(i);
                if (r.getStatutQualification() == StatutQualification.REPECHAGE) continue;
                // Candidat déjà éliminé au niveau profil : ne pas recalculer sa qualification
                if (r.getCandidat().getStatutProfil() == StatutProfilCandidat.ELIMINE) continue;
                if (i < 2) {
                    r.setStatutQualification(StatutQualification.QUALIFIE);
                } else {
                    r.setStatutQualification(StatutQualification.ELIMINE);
                    nbElimines++;
                }
            }
        }

        // Candidats sans poule affectée → éliminés (ResultatPhase uniquement)
        resultats.stream()
                .filter(r -> !pouleParCandidat.containsKey(r.getCandidat().getId()))
                .filter(r -> r.getStatutQualification() != StatutQualification.REPECHAGE)
                .forEach(r -> r.setStatutQualification(StatutQualification.ELIMINE));

        log.info("Qualification par poule : {} poule(s), {} candidat(s) marqués ELIMINE (profil différé à TERMINEE)",
                parPoule.size(), nbElimines);
    }

    /**
     * Appelé lors du passage de la soirée à TERMINEE : traduit les StatutQualification.ELIMINE
     * en StatutProfilCandidat.ELIMINE pour les candidats de cette soirée. C'est à ce moment
     * seulement que les votes en ligne sont coupés et le badge affiché côté frontend.
     */
    @Transactional
    public void appliquerEliminationsStatutProfil(UUID soireeId) {
        List<AffectationPoule> affectations = affectationPouleRepository.findByPouleSoireeId(soireeId);
        if (affectations.isEmpty()) return;

        List<Candidat> aEliminer = new ArrayList<>();
        for (AffectationPoule aff : affectations) {
            Candidat candidat = aff.getCandidat();
            UUID phaseId = aff.getPoule().getPhase().getId();
            resultatPhaseRepository.findByCandidatIdAndPhaseId(candidat.getId(), phaseId)
                    .filter(r -> r.getStatutQualification() == StatutQualification.ELIMINE)
                    .ifPresent(r -> {
                        if (candidat.getStatutProfil() == StatutProfilCandidat.ACTIF) {
                            candidat.setStatutProfil(StatutProfilCandidat.ELIMINE);
                            aEliminer.add(candidat);
                        }
                    });
        }
        if (!aEliminer.isEmpty()) {
            candidatRepository.saveAll(aEliminer);
        }
        log.info("Soirée {} → TERMINEE : {} candidat(s) passés à StatutProfilCandidat.ELIMINE",
                soireeId, aEliminer.size());
    }

    private BigDecimal calculerPointsVotesEnLigne(Candidat candidat, Phase phase, long totalVoixPayantes) {
        long voixPayantesCandidat = voteService.votesPayantsConfirmes(candidat.getId(), phase.getId());
        BigDecimal pointsPayants = calculerPointsRatio(voixPayantesCandidat, totalVoixPayantes, phase.getPointsMaxVotesEnLigne());

        long nbLikes = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(),
                List.of(bf.laterrasse.nks.domain.enums.Enums.TypeVote.SOCIAL_LIKE));
        long nbCommentaires = voteRepository.sommeVoixParCandidatEtTypes(candidat.getId(), phase.getId(),
                List.of(bf.laterrasse.nks.domain.enums.Enums.TypeVote.SOCIAL_COMMENTAIRE));
        BigDecimal pointsSociaux = POIDS_LIKE.multiply(BigDecimal.valueOf(nbLikes))
                .add(POIDS_COMMENTAIRE.multiply(BigDecimal.valueOf(nbCommentaires)));

        BigDecimal total = pointsPayants.add(pointsSociaux);
        return total.min(phase.getPointsMaxVotesEnLigne()).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculerPointsRatio(long voixCandidat, long totalVoix, BigDecimal pointsMax) {
        if (totalVoix <= 0 || voixCandidat <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(voixCandidat)
                .divide(BigDecimal.valueOf(totalVoix), 10, RoundingMode.HALF_UP)
                .multiply(pointsMax)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculerPointsJury(Candidat candidat, Phase phase) {
        List<NoteJury> notes = noteJuryRepository.findAll().stream()
                .filter(n -> n.getCandidat().getId().equals(candidat.getId()))
                .filter(n -> n.getSoiree().getPhase().getId().equals(phase.getId()))
                .toList();

        if (notes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Pour chaque juré : moyenne des totaux par passage (1 et/ou 2), puis moyenne inter-jurés.
        // Clé composite : (juryId, numeroPassage) → somme des critères pour ce passage.
        record JuryPassage(UUID juryId, int passage) {}
        var totalParJuryPassage = notes.stream().collect(java.util.stream.Collectors.groupingBy(
                n -> new JuryPassage(n.getJury().getId(), n.getNumeroPassage()),
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, NoteJury::getValeur, BigDecimal::add)));

        // Regroupe par juré : moyenne de ses passages disponibles
        var moyenneParJury = totalParJuryPassage.entrySet().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e.getKey().juryId(),
                        java.util.stream.Collectors.averagingDouble(e -> e.getValue().doubleValue())));

        BigDecimal sommeMoyennes = moyenneParJury.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal moyenneInterJury = sommeMoyennes.divide(
                BigDecimal.valueOf(moyenneParJury.size()), 10, RoundingMode.HALF_UP);

        return moyenneInterJury.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(phase.getPointsMaxJury())
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** Recalcul immédiat déclenché par la confirmation d'un paiement de vote (événement asynchrone). */
    @EventListener
    @Async
    @Transactional
    public void onClassementRefresh(ClassementRefreshEvent event) {
        try {
            calculerClassementPhase(event.phaseId());
            mettreAJourClassementGlobal(event.editionId());
        } catch (Exception e) {
            log.error("Échec recalcul classement immédiat (phase {}) : {}", event.phaseId(), e.getMessage());
        }
    }

    @Transactional
    @bf.laterrasse.nks.aop.Auditable(action = "CLASSEMENT_GLOBAL_MIS_A_JOUR", entite = "Classement")
    public void mettreAJourClassementGlobal(UUID editionId) {
        List<ResultatPhase> tousResultats = resultatPhaseRepository.findAll().stream()
                .filter(r -> r.getPhase().getEdition().getId().equals(editionId))
                .toList();

        var totauxParCandidat = tousResultats.stream().collect(java.util.stream.Collectors.groupingBy(
                r -> r.getCandidat().getId(),
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, ResultatPhase::getTotalPoints, BigDecimal::add)));

        List<Classement> classements = totauxParCandidat.entrySet().stream()
                .map(e -> {
                    Candidat candidat = candidatRepository.findById(e.getKey()).orElseThrow();
                    Classement classement = classementRepository.findByCandidatIdAndEditionId(e.getKey(), editionId)
                            .orElse(Classement.builder().candidat(candidat).edition(candidat.getEdition()).build());
                    classement.setTotalPointsCumules(e.getValue());
                    classement.setDateDerniereMiseAJour(Instant.now());
                    return classement;
                })
                .sorted(Comparator.comparing(Classement::getTotalPointsCumules).reversed())
                .toList();

        int rang = 1;
        for (Classement c : classements) {
            c.setRangGlobal(rang++);
        }
        classementRepository.saveAll(classements);
    }
}
