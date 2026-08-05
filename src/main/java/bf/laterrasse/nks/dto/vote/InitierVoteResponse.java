package bf.laterrasse.nks.dto.vote;

import java.math.BigDecimal;
import java.util.UUID;

public record InitierVoteResponse(UUID paiementId, String urlPaiement, BigDecimal montantTotal, long expireDansSecondes) {
}
