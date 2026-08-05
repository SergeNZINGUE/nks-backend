package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Edition;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Vote;
import bf.laterrasse.nks.domain.enums.Enums.StatutEdition;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import bf.laterrasse.nks.gateway.social.EngagementSocial;
import bf.laterrasse.nks.gateway.social.SocialVoteProvider;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Polling périodique des votes sociaux (décision client : API Facebook/TikTok réelle,
 * remplace la saisie manuelle H3). Désactivé par défaut via le paramètre plateforme
 * SOCIAL_VOTES_POLLING_ACTIF tant que les apps développeur Meta/TikTok du client ne sont
 * pas approuvées (cf. SocialVoteProvider). Une fois actif, tourne toutes les 15 minutes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SocialVoteIngestionService {

    private final EditionRepository editionRepository;
    private final PhaseRepository phaseRepository;
    private final CandidatRepository candidatRepository;
    private final VoteRepository voteRepository;
    private final ParametrePlateformeService parametrePlateformeService;
    private final List<SocialVoteProvider> providers; // Facebook + TikTok injectés automatiquement

    @Scheduled(fixedRateString = "PT15M")
    @Transactional
    public void ingererVotesSociaux() {
        if (!parametrePlateformeService.getBoolean("SOCIAL_VOTES_POLLING_ACTIF", false)) {
            return;
        }

        Optional<Edition> editionEnCours = editionRepository.findByStatut(StatutEdition.EN_COURS);
        if (editionEnCours.isEmpty()) {
            return;
        }

        List<Phase> phasesActives = phaseRepository.findByEditionIdOrderByOrdreAsc(editionEnCours.get().getId())
                .stream().filter(Phase::isVoteActif).toList();
        if (phasesActives.isEmpty()) {
            return;
        }

        List<Candidat> candidats = candidatRepository.findAll().stream()
                .filter(c -> c.getEdition().getId().equals(editionEnCours.get().getId()))
                .filter(c -> c.getStatutProfil() == StatutProfilCandidat.ACTIF)
                .filter(c -> c.getPostIdFacebook() != null || c.getPostIdTiktok() != null)
                .toList();

        for (Phase phase : phasesActives) {
            for (Candidat candidat : candidats) {
                relerverEtIngerer(candidat, phase);
            }
        }
    }

    private void relerverEtIngerer(Candidat candidat, Phase phase) {
        for (SocialVoteProvider provider : providers) {
            String postId = "FACEBOOK".equals(provider.getNomPlateforme())
                    ? candidat.getPostIdFacebook() : candidat.getPostIdTiktok();
            if (postId == null) {
                continue;
            }
            provider.relever(postId).ifPresent(engagement -> ingererDelta(candidat, phase, engagement));
        }
    }

    private void ingererDelta(Candidat candidat, Phase phase, EngagementSocial engagement) {
        long dejaLikes = voteRepository.sommeVoixParCandidatEtTypes(
                candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_LIKE));
        long dejaCommentaires = voteRepository.sommeVoixParCandidatEtTypes(
                candidat.getId(), phase.getId(), List.of(TypeVote.SOCIAL_COMMENTAIRE));

        long deltaLikes = engagement.nombreLikes() - dejaLikes;
        long deltaCommentaires = engagement.nombreCommentaires() - dejaCommentaires;

        if (deltaLikes > 0) {
            voteRepository.save(Vote.builder()
                    .candidat(candidat).phase(phase)
                    .typeVote(TypeVote.SOCIAL_LIKE)
                    .nombreVoix((int) deltaLikes)
                    .sourceExterneId(engagement.snapshotId() + "-likes")
                    .build());
        }
        if (deltaCommentaires > 0) {
            voteRepository.save(Vote.builder()
                    .candidat(candidat).phase(phase)
                    .typeVote(TypeVote.SOCIAL_COMMENTAIRE)
                    .nombreVoix((int) deltaCommentaires)
                    .sourceExterneId(engagement.snapshotId() + "-comments")
                    .build());
        }
        if (deltaLikes > 0 || deltaCommentaires > 0) {
            log.info("Votes sociaux {} ingérés pour candidat {} : +{} likes, +{} commentaires",
                    engagement.plateforme(), candidat.getCodeCandidat(), Math.max(deltaLikes, 0), Math.max(deltaCommentaires, 0));
        }
    }
}
