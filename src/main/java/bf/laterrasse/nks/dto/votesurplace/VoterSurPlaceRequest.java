package bf.laterrasse.nks.dto.votesurplace;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * `telephoneVotant`/`positionLatitude`/`positionLongitude`/`positionPrecisionM` sont
 * purement déclaratifs et facultatifs — jamais utilisés pour bloquer un vote, seulement
 * conservés pour audit a posteriori (cf. VoteSurPlaceService, échange du 13/09/2026 sur le
 * risque qu'un jeton soit intercepté et utilisé par un tiers à la place du client).
 */
public record VoterSurPlaceRequest(
        @NotNull UUID candidatId,
        String telephoneVotant,
        BigDecimal positionLatitude,
        BigDecimal positionLongitude,
        BigDecimal positionPrecisionM
) {
}
