package bf.laterrasse.nks.dto.paiement;

import java.util.UUID;

public record InitierPaiementResponse(UUID paiementId, String urlPaiement, long expireDansSecondes) {
}
