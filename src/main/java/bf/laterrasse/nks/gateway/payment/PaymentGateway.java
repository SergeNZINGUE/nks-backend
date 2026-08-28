package bf.laterrasse.nks.gateway.payment;

import java.math.BigDecimal;

/**
 * Abstraction du gateway de paiement Mobile Money (§12.4, ADR-05).
 * Implémentation prioritaire : LigdiCash. Permet de basculer vers CinetPay/FedaPay
 * sans impacter le reste du code (R3 : mitigation du risque de retard d'intégration).
 *
 * Protocole LigdiCash :
 * - initierPaiement → createInvoice → retourne token + redirect_url
 * - confirmerPaiement(token) → confirmInvoice → source de vérité du statut
 * - Webhook : simple notification sans signature — en extraire le token,
 *   puis appeler confirmerPaiement. Ne jamais se fier au contenu du webhook lui-même.
 */
public interface PaymentGateway {

    InitiationPaiement initierPaiement(BigDecimal montant, String telephone,
                                        String idempotencyKey, String notifyUrl,
                                        String returnUrl, String cancelUrl);

    /** Source de vérité — à appeler à chaque callback ET en polling de secours. */
    ConfirmationPaiement confirmerPaiement(String token);

    /** Extrait le token LigdiCash du payload webhook (JSON ou form-encoded). */
    String extraireTokenWebhook(String payload);

    ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant);
}
