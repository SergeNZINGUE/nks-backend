package bf.laterrasse.nks.gateway.payment;

import java.math.BigDecimal;

public record ConfirmationPaiement(
        boolean succes,
        String idempotencyKey,
        String referenceOperateur,
        String statutOperateur,
        BigDecimal montant,
        String telephonePayeur,
        String rawPayload
) {
}
