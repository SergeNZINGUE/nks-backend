package bf.laterrasse.nks.dto.billetterie;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code ticketAccessToken} : jeton d'accès billets scopé à CETTE seule réservation
 * (scope "read" uniquement, jamais "cancel" — cf. audit sécurité IDOR billetterie).
 * Ne doit jamais être recopié dans une URL (return_url paiement, query param, etc.),
 * uniquement transmis dans ce corps JSON.
 */
public record ReservationResponse(
        UUID reservationId,
        UUID paiementId,
        String urlPaiement,
        BigDecimal montantTotal,
        String statut,
        String ticketAccessToken,
        long ticketAccessTokenExpiresIn
) {
}
