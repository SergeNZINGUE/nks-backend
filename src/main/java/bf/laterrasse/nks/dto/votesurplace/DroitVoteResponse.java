package bf.laterrasse.nks.dto.votesurplace;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.domain.enums.Enums.StatutDroitVote;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse publique pour /vote-sur-place — gère nativement 0, 1 ou N droits (BASE + BONUS
 * confondus) pour un même billet. `candidats` n'est renseigné que si au moins un droit est
 * encore DISPONIBLE (liste des candidats de cette soirée, pour le choix du vote) — vide
 * sinon.
 */
public record DroitVoteResponse(
        String nomSpectateur,
        int nbVotesDisponibles,
        int nbVotesTotal,
        List<VoteExprimeResponse> votesExprimes,
        List<CandidatPublicResponse> candidats
) {
    public record VoteExprimeResponse(UUID candidatId, String candidatCode, Instant dateVote) {}

    public static DroitVoteResponse from(List<DroitVoteSurPlace> droits, String nomSpectateur,
                                          List<Candidat> candidatsDisponibles) {
        int nbDisponibles = (int) droits.stream().filter(d -> d.getStatut() == StatutDroitVote.DISPONIBLE).count();
        List<VoteExprimeResponse> votesExprimes = droits.stream()
                .filter(d -> d.getStatut() == StatutDroitVote.UTILISE)
                .sorted((a, b) -> a.getDateVote().compareTo(b.getDateVote()))
                .map(d -> new VoteExprimeResponse(
                        d.getCandidat() != null ? d.getCandidat().getId() : null,
                        d.getCandidat() != null ? d.getCandidat().getCodeCandidat() : null,
                        d.getDateVote()))
                .toList();
        List<CandidatPublicResponse> candidats = nbDisponibles > 0
                ? candidatsDisponibles.stream().map(CandidatPublicResponse::from).toList()
                : List.of();
        return new DroitVoteResponse(nomSpectateur, nbDisponibles, droits.size(), votesExprimes, candidats);
    }
}
