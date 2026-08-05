package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.*;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.StatutQualification;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * WF-07 — Calcul du classement par phase. Interprétation retenue pour l'agrégation des
 * votes sociaux (documentée car le cahier des charges est ambigu à ce sujet, cf. rapport
 * §RM-19/20/21 et WF-05) :
 *   - Votes payants  : ratio (voix_candidat / total_voix_phase) × points_max_votes_en_ligne
 *   - Votes sociaux  : likes × 0,25 + commentaires × 0,75, ADDITIONNÉS aux points votes
 *                      payants dans la même enveloppe "votes en ligne", plafonnés à
 *                      points_max_votes_en_ligne (pour ne jamais dépasser le poids alloué
 *                      à la phase). Cette règle est configurable/à valider avec le client
 *                      si l'intention réelle diffère.
 *   - Vote sur place : ratio (voix_sur_place_candidat / total_voix_sur_place) × points_max_public
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

    @Transactional
    public List<ResultatPhase> calculerClassementPhase(UUID phaseId) {
        Phase phase = phaseRepository.findById(phaseId)
                .orElseThrow(() -> new IllegalArgumentException("Phase introuvable : " + phaseId));

        List<Candidat> candidats = candidatRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(phase.getEdition().getId()))
                .filter(c -> c.getStatutProfil() == StatutProfilCandidat.ACTIF
                        || c.getStatutProfil() == StatutProfilCandidat.FINALISTE
                        || c.getStatutProfil() == StatutProfilCandidat.GAGNANT)
                .toList();

        long totalVoixPayantes = voteService.totalVotesPayantsConfirmes(phaseId);
        long totalVoixSurPlace = voteService.totalVotesSurPlace(phaseId);

        List<ResultatPhase> resultats = candidats.stream()
                .map(candidat -> calculerPourCandidat(candidat, phase, totalVoixPayantes, totalVoixSurPlace))
                .sorted(Comparator.comparing(ResultatPhase::getTotalPoints).reversed())
                .toList();

        int rang = 1;
        for (ResultatPhase r : resultats) {
            r.setRang(rang++);
            if (r.getStatutQualification() == StatutQualification.EN_ATTENTE) {
                r.setStatutQualification(StatutQualification.QUALIFIE); // qualification définitive laissée à l'admin (repêchage, WF-08)
            }
        }
        resultatPhaseRepository.saveAll(resultats);

        log.info("Classement recalculé pour la phase {} ({} candidats)", phaseId, resultats.size());
        return resultats;
    }

    private ResultatPhase calculerPourCandidat(Candidat candidat, Phase phase, long totalVoixPayantes, long totalVoixSurPlace) {
        BigDecimal pointsVotesEnLigne = calculerPointsVotesEnLigne(candidat, phase, totalVoixPayantes);
        BigDecimal pointsPublic = calculerPointsRatio(
                voteService.votesSurPlace(candidat.getId(), phase.getId()), totalVoixSurPlace, phase.getPointsMaxPublic());
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
        // Récupère toutes les notes du candidat pour les soirées de cette phase
        List<NoteJury> notes = noteJuryRepository.findAll().stream()
                .filter(n -> n.getCandidat().getId().equals(candidat.getId()))
                .filter(n -> n.getSoiree().getPhase().getId().equals(phase.getId()))
                .toList();

        if (notes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Moyenne par juré du total (somme des critères), puis moyenne inter-jurés
        var totalParJury = notes.stream().collect(java.util.stream.Collectors.groupingBy(
                n -> n.getJury().getId(),
                java.util.stream.Collectors.reducing(BigDecimal.ZERO, NoteJury::getValeur, BigDecimal::add)));

        BigDecimal sommeTotaux = totalParJury.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal moyenne = sommeTotaux.divide(BigDecimal.valueOf(totalParJury.size()), 10, RoundingMode.HALF_UP);

        return moyenne.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(phase.getPointsMaxJury())
                .setScale(4, RoundingMode.HALF_UP);
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
