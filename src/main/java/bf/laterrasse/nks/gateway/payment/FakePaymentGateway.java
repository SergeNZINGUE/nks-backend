package bf.laterrasse.nks.gateway.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Implémentation de test (profil `nks.payment.provider=fake`) — aucun appel réseau,
 * confirmation systématiquement positive. Utilisée en tests d'intégration (Testcontainers)
 * et pour le développement local sans accès sandbox LigdiCash.
 */
@Component
@ConditionalOnProperty(prefix = "nks.payment", name = "provider", havingValue = "fake")
public class FakePaymentGateway implements PaymentGateway {

    @Override
    public InitiationPaiement initierPaiement(BigDecimal montant, String telephone,
                                               String idempotencyKey, String notifyUrl,
                                               String returnUrl, String cancelUrl) {
        return new InitiationPaiement("https://fake-payment.local/pay/" + idempotencyKey,
                "FAKE-" + UUID.randomUUID());
    }

    @Override
    public ConfirmationPaiement confirmerPaiement(String token) {
        return new ConfirmationPaiement(true, null, token, "completed",
                BigDecimal.ZERO, "+22600000000", null, "00", null);
    }

    @Override
    public String extraireTokenWebhook(String payload) {
        return payload != null && !payload.isBlank() ? payload.trim() : null;
    }

    @Override
    public ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant) {
        return new ResultatRemboursement(true, "FAKE-REFUND-" + UUID.randomUUID(), "OK (fake)");
    }
}
