package bf.laterrasse.nks.dto.votesurplace;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * `telephoneVotant`/`positionLatitude`/`positionLongitude`/`positionPrecisionM` sont
 * purement déclaratifs et facultatifs — jamais utilisés pour bloquer un vote, seulement
 * conservés pour audit a posteriori (cf. VoteSurPlaceService : le risque qu'un jeton soit
 * intercepté et utilisé par un tiers à la place du client ne peut pas être exclu côté serveur).
 */
public record VoterSurPlaceRequest(
        @NotNull UUID candidatId,
        @Size(max = 30) @Pattern(regexp = "^[+0-9 .-]*$") String telephoneVotant,
        BigDecimal positionLatitude,
        BigDecimal positionLongitude,
        BigDecimal positionPrecisionM
) {
}
