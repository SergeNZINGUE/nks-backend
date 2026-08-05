package bf.laterrasse.nks.dto.billetterie;

import java.math.BigDecimal;
import java.util.UUID;

public record ReservationResponse(
        UUID reservationId,
        UUID paiementId,
        String urlPaiement,
        BigDecimal montantTotal,
        String statut
) {
}
