package bf.laterrasse.nks.dto.votesurplace;

import bf.laterrasse.nks.domain.DroitVoteSurPlace;
import bf.laterrasse.nks.dto.candidat.CandidatPublicResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Réponse publique pour /vote-sur-place. `candidats` n'est renseigné que lorsque le droit
 * est encore DISPONIBLE (liste des candidats de cette soirée, pour le choix du vote) —
 * vide une fois le vote exprimé.
 */
public record DroitVoteResponse(
        UUID droitId,
        String statut,
        String nomSpectateur,
        Instant dateEmission,
        boolean lienWhatsappEnvoye,
        UUID candidatVoteId,
        Instant dateVote,
        List<CandidatPublicResponse> candidats
) {
    public static DroitVoteResponse from(DroitVoteSurPlace d, String nomSpectateur, List<bf.laterrasse.nks.domain.Candidat> candidatsDisponibles) {
        return new DroitVoteResponse(
                d.getId(),
                d.getStatut().name(),
                nomSpectateur,
                d.getDateEmission(),
                d.isLienWhatsappEnvoye(),
                d.getCandidat() != null ? d.getCandidat().getId() : null,
                d.getDateVote(),
                candidatsDisponibles.stream().map(CandidatPublicResponse::from).toList());
    }
}
