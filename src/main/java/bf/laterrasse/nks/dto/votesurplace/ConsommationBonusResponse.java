package bf.laterrasse.nks.dto.votesurplace;

/**
 * Réponse de POST /caisse/consommations-bonus. `nouveauVoteDebloque` n'est true que lorsque
 * cet appel précis a fait franchir un nouveau palier (seuil de consommations) — c'est le seul
 * cas où une notification WhatsApp est envoyée au client, cf. VoteSurPlaceService.
 */
public record ConsommationBonusResponse(
        int nbConsommationsSupplementaires,
        int seuil,
        Integer plafond,
        int nbVotesBonusDebloquesAuTotal,
        boolean nouveauVoteDebloque,
        int nbVotesDisponiblesTotal
) {
}
