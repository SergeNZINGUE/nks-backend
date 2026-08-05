package bf.laterrasse.nks.gateway.payment;

import bf.laterrasse.nks.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Intégration LigdiCash (ADR-05, prioritaire — H2). ⚠️ Les noms d'endpoints et le format
 * exact des payloads doivent être vérifiés/ajustés contre la documentation officielle
 * LigdiCash et un environnement sandbox avant mise en production (risque R3 du rapport :
 * "documentation variable"). La structure ci-dessous suit le schéma REST classique des
 * gateways Mobile Money ouest-africaines (invoice + webhook HMAC) et sert de base solide
 * à ajuster lors de l'intégration réelle.
 */
@Component
@ConditionalOnProperty(prefix = "nks.payment", name = "provider", havingValue = "ligdicash", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LigdiCashGateway implements PaymentGateway {

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient client() {
        return WebClient.builder()
                .baseUrl(paymentProperties.getLigdicash().getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + paymentProperties.getLigdicash().getApiKey())
                .build();
    }

    @Override
    public InitiationPaiement initierPaiement(BigDecimal montant, String telephone,
                                               String idempotencyKey, String callbackUrl) {
        Map<String, Object> body = Map.of(
                "amount", montant,
                "currency", "XOF",
                "customer_phone", telephone,
                "client_reference", idempotencyKey, // corrélé au retour webhook pour retrouver le Paiement
                "callback_url", callbackUrl,
                "description", "NKS - Night Karaoke Stars"
        );

        JsonNode response = client().post()
                .uri("/invoices")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String urlPaiement = response != null && response.has("payment_url")
                ? response.get("payment_url").asText() : null;
        String reference = response != null && response.has("token")
                ? response.get("token").asText() : idempotencyKey;

        return new InitiationPaiement(urlPaiement, reference);
    }

    @Override
    public boolean verifierWebhook(String payload, String signature) {
        String secret = paymentProperties.getLigdicash().getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("LIGDICASH_WEBHOOK_SECRET non configuré — signature non vérifiée (dev uniquement)");
            return true;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return computedHex.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.error("Erreur de vérification de signature webhook LigdiCash", e);
            return false;
        }
    }

    @Override
    public ConfirmationPaiement traiterConfirmation(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String statut = node.path("status").asText("");
            boolean succes = "SUCCESS".equalsIgnoreCase(statut) || "COMPLETED".equalsIgnoreCase(statut);
            return new ConfirmationPaiement(
                    succes,
                    node.path("client_reference").asText(null),
                    node.path("token").asText(null),
                    statut,
                    node.has("amount") ? new BigDecimal(node.get("amount").asText()) : null,
                    node.path("customer_phone").asText(null),
                    payload
            );
        } catch (Exception e) {
            log.error("Payload webhook LigdiCash illisible", e);
            return new ConfirmationPaiement(false, null, null, "PAYLOAD_INVALIDE", null, null, payload);
        }
    }

    @Override
    public ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant) {
        try {
            JsonNode response = client().post()
                    .uri("/refunds")
                    .bodyValue(Map.of("token", referenceOperateur, "amount", montant))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String refRemb = response != null ? response.path("refund_id").asText(null) : null;
            return new ResultatRemboursement(true, refRemb, "Remboursement initié");
        } catch (Exception e) {
            log.error("Échec remboursement LigdiCash pour {}", referenceOperateur, e);
            return new ResultatRemboursement(false, null, e.getMessage());
        }
    }
}
