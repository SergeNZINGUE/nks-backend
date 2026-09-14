package bf.laterrasse.nks.dto.votesurplace;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne par vote sur place exprimé pour une soirée — permet de recouper a posteriori
 * le téléphone déclaré au moment du vote (`telephoneVotantDeclare`, jamais vérifié en temps
 * réel, cf. VoteSurPlaceService) avec le téléphone réel du billet (`telephoneBillet`).
 * `telephonesCorrespondent` vaut `true` soit parce qu'ils correspondent, soit parce
 * qu'aucun téléphone n'a été déclaré au vote (rien à contredire) — `false` uniquement
 * en cas de discordance réelle et vérifiable.
 */
public record ReconciliationVoteResponse(
        UUID ticketId,
        String nomSpectateur,
        String telephoneBillet,
        String telephoneVotantDeclare,
        boolean telephonesCorrespondent,
        BigDecimal positionLatitude,
        BigDecimal positionLongitude,
        BigDecimal positionPrecisionM,
        UUID candidatId,
        String candidatCode,
        Instant dateVote
) {
}
