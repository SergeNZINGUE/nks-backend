package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Phase;
import bf.laterrasse.nks.domain.Vote;
import bf.laterrasse.nks.domain.VotePayant;
import bf.laterrasse.nks.domain.enums.Enums.StatutProfilCandidat;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.domain.enums.Enums.TypePaiement;
import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import bf.laterrasse.nks.dto.vote.InitierVoteRequest;
import bf.laterrasse.nks.dto.vote.InitierVoteResponse;
import bf.laterrasse.nks.event.PaiementConfirmeEvent;
import bf.laterrasse.nks.exception.ConflitEtatException;
import bf.laterrasse.nks.exception.ResourceNotFoundException;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * WF-04 (vote payant). Le crédit du vote est purement déclaratif dès l'initiation : le
 * Vote/VotePayant est créé lié à un Paiement PENDING, et ne compte dans le classement que
 * lorsque le paiement passe COMPLETED (cf. VoteRepository.sommeVoixPayantesConfirmees).
 * Cela évite un état intermédiaire "vote sans paiement" tout en gardant une seule écriture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteService {

    private static final List<TypeVote> TYPES_SOCIAUX = List.of(TypeVote.SOCIAL_LIKE, TypeVote.SOCIAL_COMMENTAIRE);

    private final CandidatRepository candidatRepository;
    private final PhaseRepository phaseRepository;
    private final VoteRepository voteRepository;
    private final VotePayantRepository votePayantRepository;
    private final PaiementService paiementService;
    private final ParametrePlateformeService parametrePlateformeService;
    private final NotificationService notificationService;

    @Transactional
    public InitierVoteResponse initierVotePayant(InitierVoteRequest request) {
        Candidat candidat = candidatRepository.findById(request.candidatId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidat introuvable"));
        Phase phase = phaseRepository.findById(request.phaseId())
                .orElseThrow(() -> new ResourceNotFoundException("Phase introuvable"));

        if (candidat.getStatutProfil() != StatutProfilCandidat.ACTIF) {
            throw new ValidationMetierException("Ce candidat n'est pas actif");
        }
        if (!phase.isVoteActif()) {
            throw new ConflitEtatException("La fenêtre de vote de cette phase n'est pas active (RM-24)");
        }

        int prixVote = parametrePlateformeService.getInt("PRIX_VOTE_FCFA", 100);
        BigDecimal montant = BigDecimal.valueOf((long) request.nbVotes() * prixVote);

        String telephone = request.telephone() != null ? request.telephone() : "";
        PaiementInitie paiementInitie = paiementService.creerEtDemarrer(
                TypePaiement.VOTE, montant, telephone, null);

        Vote vote = Vote.builder()
                .candidat(candidat)
                .phase(phase)
                .typeVote(TypeVote.EN_LIGNE_PAYANT)
                .nombreVoix(request.nbVotes())
                .sourceTelephone(telephone)
                .build();
        vote = voteRepository.save(vote);

        votePayantRepository.save(VotePayant.builder()
                .vote(vote)
                .paiement(paiementInitie.paiement())
                .nombreVotesAchetes(request.nbVotes())
                .montantTotal(montant)
                .telephoneVotant(telephone)
                .build());

        return new InitierVoteResponse(paiementInitie.paiement().getId(), paiementInitie.urlPaiement(),
                montant, 900);
    }

    @EventListener
    @org.springframework.transaction.annotation.Transactional
    public void onPaiementConfirme(PaiementConfirmeEvent event) {
        if (event.typePaiement() != TypePaiement.VOTE) {
            return;
        }
        votePayantRepository.findByPaiementId(event.paiementId()).ifPresent(vp -> {
            String telephonePayeur = event.telephonePayeur();
            if (telephonePayeur != null && !telephonePayeur.isBlank()) {
                int seuil = parametrePlateformeService.getInt("MAX_VOTES_PAYANTS_PAR_TELEPHONE_PAR_HEURE", 20);
                Instant depuis = Instant.now().minus(1, ChronoUnit.HOURS);
                long dejaAchetes = votePayantRepository.sommeVotesConfirmesParTelephonePayeur(telephonePayeur, depuis);
                if (dejaAchetes > seuil) {
                    vp.setFraudeDetectee(true);
                    votePayantRepository.save(vp);
                    log.warn("Anti-fraude RM-25 : paiement {} marqué fraudeDetectee — {} achats sur {} autorisés (MSISDN payeur {})",
                            event.paiementId(), dejaAchetes, seuil, telephonePayeur);
                    notificationService.envoyerSms(null, vp.getTelephoneVotant(), TypeNotification.PAIEMENT_CONFIRME,
                            "NKS : paiement reçu. Vos votes feront l'objet d'un contrôle anti-fraude.");
                    return;
                }
            }
            notificationService.envoyerSms(null, vp.getTelephoneVotant(), TypeNotification.PAIEMENT_CONFIRME,
                    "NKS : merci ! Vos " + vp.getNombreVotesAchetes() + " votes ont été crédités.");
        });
    }

    // ---- Lecture / classement ----

    public long votesPayantsConfirmes(java.util.UUID candidatId, java.util.UUID phaseId) {
        return voteRepository.sommeVoixPayantesConfirmees(candidatId, phaseId);
    }

    public long totalVotesPayantsConfirmes(java.util.UUID phaseId) {
        return voteRepository.totalVoixPayantesConfirmeesPourPhase(phaseId);
    }

    public long votesSociaux(java.util.UUID candidatId, java.util.UUID phaseId) {
        return voteRepository.sommeVoixParCandidatEtTypes(candidatId, phaseId, TYPES_SOCIAUX);
    }

    public long votesSurPlace(java.util.UUID candidatId, java.util.UUID phaseId) {
        return voteRepository.sommeVoixParCandidatEtTypes(candidatId, phaseId, List.of(TypeVote.PUBLIC_SUR_PLACE));
    }

    public long totalVotesSurPlace(java.util.UUID phaseId) {
        return voteRepository.totalVoixPourPhaseEtTypes(phaseId, List.of(TypeVote.PUBLIC_SUR_PLACE));
    }
}
