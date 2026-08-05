package bf.laterrasse.nks.gateway.payment;

import java.math.BigDecimal;

/**
 * Abstraction du gateway de paiement Mobile Money (§12.4, ADR-05). Implémentation
 * prioritaire : LigdiCash. Permet de basculer vers CinetPay/FedaPay sans impacter le
 * reste du code (R3 : mitigation du risque de retard d'intégration LigdiCash).
 */
public interface PaymentGateway {

    InitiationPaiement initierPaiement(BigDecimal montant, String telephone,
                                        String idempotencyKey, String callbackUrl);

    boolean verifierWebhook(String payload, String signature);

    ConfirmationPaiement traiterConfirmation(String payload);

    ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant);
}
