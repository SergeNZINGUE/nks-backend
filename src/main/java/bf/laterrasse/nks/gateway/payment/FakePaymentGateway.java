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
    public InitiationPaiement initierPaiement(BigDecimal montant, String telephone, String idempotencyKey, String callbackUrl) {
        return new InitiationPaiement("https://fake-payment.local/pay/" + idempotencyKey, "FAKE-" + UUID.randomUUID());
    }

    @Override
    public boolean verifierWebhook(String payload, String signature) {
        return true;
    }

    @Override
    public ConfirmationPaiement traiterConfirmation(String payload) {
        return new ConfirmationPaiement(true, payload, "FAKE-REF", "COMPLETED", BigDecimal.ZERO, "+22600000000", payload);
    }

    @Override
    public ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant) {
        return new ResultatRemboursement(true, "FAKE-REFUND-" + UUID.randomUUID(), "OK (fake)");
    }
}
