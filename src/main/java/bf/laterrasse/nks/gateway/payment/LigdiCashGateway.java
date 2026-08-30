package bf.laterrasse.nks.gateway.payment;

import bf.laterrasse.nks.config.PaymentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Intégration LigdiCash — API réelle (ADR-05).
 *
 * Endpoints :
 *   POST /pay/v01/redirect/checkout-invoice/create  → crée la facture, retourne token + redirect_url
 *   POST /pay/v01/redirect/checkout-invoice/confirm → source de vérité du statut (à appeler
 *       à chaque callback ET en polling de secours — ne jamais se fier au contenu du webhook)
 *
 * Protocole webhook :
 *   LigdiCash envoie 2 POST par événement (form-encoded + JSON), sans signature.
 *   La déduplication est assurée par la table ligdicash_callbacks (contrainte UNIQUE token).
 *
 * Codes de réponse clés (response_code) :
 *   "00"     → succès
 *   "Code02" → montant hors plage ou facture introuvable (côté confirm)
 *   "Code10/11" → hash absent/invalide (bug d'intégration)
 *   "Code14" → identifiants wallet erronés
 *   "Code15" → mauvais OTP
 *   "Code16" → OTP expiré
 *   "Code17" → montant OTP incorrect
 *   "Code18" → solde insuffisant
 *
 * Statuts confirm : pending / completed / notcompleted
 */
@Component
@ConditionalOnProperty(prefix = "nks.payment", name = "provider", havingValue = "ligdicash", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LigdiCashGateway implements PaymentGateway {

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebClient client() {
        PaymentProperties.LigdiCash cfg = paymentProperties.getLigdicash();
        String authToken = (cfg.getApiSecret() != null && !cfg.getApiSecret().isBlank())
                ? cfg.getApiSecret()
                : cfg.getApiKey();

        return WebClient.builder()
                .baseUrl(cfg.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + authToken)
                .defaultHeader("Apikey", cfg.getApiKey())
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public InitiationPaiement initierPaiement(BigDecimal montant, String telephone,
                                               String idempotencyKey, String notifyUrl,
                                               String returnUrl, String cancelUrl) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", "NKS - Night Karaoke Stars");
        item.put("description", "Paiement NKS");
        item.put("quantity", 1);
        item.put("unit_price", montant.intValue());
        item.put("total_price", montant.intValue());

        Map<String, Object> invoice = new HashMap<>();
        invoice.put("items", java.util.List.of(item));
        invoice.put("total_amount", montant.intValue());
        invoice.put("devise", "XOF");
        invoice.put("description", "NKS - Night Karaoke Stars");
        invoice.put("customer", telephone != null ? telephone : "");
        invoice.put("customer_firstname", "Client");
        invoice.put("customer_lastname", "NKS");
        invoice.put("customer_email", "noreply@laterrasse.bf");

        Map<String, Object> store = new HashMap<>();
        store.put("name", "Night Karaoke Stars");
        store.put("website_url", "https://laterrasse.bf");

        Map<String, Object> actions = new HashMap<>();
        actions.put("cancel_url", cancelUrl);
        actions.put("return_url", returnUrl);
        actions.put("callback_url", notifyUrl);

        Map<String, Object> customData = new HashMap<>();
        customData.put("external_id", idempotencyKey);

        Map<String, Object> commande = new HashMap<>();
        commande.put("invoice", invoice);
        commande.put("store", store);
        commande.put("actions", actions);
        commande.put("custom_data", customData);

        Map<String, Object> body = Map.of("commande", commande);

        try {
            JsonNode response = client().post()
                    .uri("/redirect/checkout-invoice/create")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("Réponse nulle de LigdiCash createInvoice");
            }

            String responseCode = response.path("response_code").asText("");
            if (!"00".equals(responseCode)) {
                String wiki = response.path("wiki").asText("");
                log.error("LigdiCash createInvoice échoué — response_code={}, wiki={}", responseCode, wiki);
                throw new RuntimeException("LigdiCash createInvoice : " + responseCode
                        + " — " + response.path("response_text").asText("") + " (wiki: " + wiki + ")");
            }

            String token = response.path("token").asText(null);
            String responseText = response.path("response_text").asText("");
            String redirectUrl = response.path("redirect_url").asText(null);

            // Dans l'API Hosted de LigdiCash, l'URL de paiement est renvoyée dans response_text ou redirect_url
            if (redirectUrl == null || redirectUrl.isBlank()) {
                if (responseText.startsWith("http")) {
                    redirectUrl = responseText;
                } else if (token != null) {
                    redirectUrl = "https://app.ligdicash.com/pay/v01/redirect/checkout-invoice/" + token;
                }
            }

            log.info("LigdiCash createInvoice réussi — token={} redirectUrl={}", token, redirectUrl);
            return new InitiationPaiement(redirectUrl, token);

        } catch (WebClientResponseException e) {
            log.error("LigdiCash createInvoice HTTP {} — {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Erreur HTTP LigdiCash createInvoice : " + e.getStatusCode(), e);
        }
    }

    @Override
    public ConfirmationPaiement confirmerPaiement(String token) {
        try {
            JsonNode response = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/redirect/checkout-invoice/confirm/")
                            .queryParam("invoiceToken", token)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null) {
                log.error("LigdiCash confirmInvoice réponse nulle pour token={}", token);
                return echecConfirmation(token, null, "Réponse nulle");
            }

            String responseCode = response.path("response_code").asText("");
            String responseText = response.path("response_text").asText("");
            String status = response.path("status").asText("pending");

            boolean succes = "completed".equalsIgnoreCase(status) && "00".equals(responseCode);

            String montantStr = response.path("amount").asText(null);
            if (montantStr == null || montantStr.isBlank()) {
                montantStr = response.path("total_amount").asText(null);
            }
            BigDecimal montant = null;
            if (montantStr != null && !montantStr.isBlank()) {
                try { montant = new BigDecimal(montantStr); } catch (NumberFormatException ignored) {}
            }

            String telephone = response.path("customer_phone_number").asText(null);
            if (telephone == null || telephone.isBlank()) {
                telephone = response.path("customer").asText(null);
            }

            log.info("LigdiCash confirmInvoice token={} → status={} code={}", token, status, responseCode);

            return new ConfirmationPaiement(
                    succes,
                    null,
                    token,
                    status,
                    montant,
                    telephone,
                    response.toString(),
                    responseCode,
                    succes ? null : responseText
            );

        } catch (WebClientResponseException e) {
            log.error("LigdiCash confirmInvoice HTTP {} pour token={}", e.getStatusCode(), token);
            return echecConfirmation(token, null, "HTTP " + e.getStatusCode());
        }
    }

    @Override
    public String extraireTokenWebhook(String payload) {
        if (payload == null || payload.isBlank()) return null;

        // Tentative JSON
        try {
            JsonNode node = objectMapper.readTree(payload.trim());
            String token = node.path("token").asText(null);
            if (token != null && !token.isBlank()) return token;
        } catch (Exception ignored) {}

        // Fallback form-encoded (ex. token=abc123&status=completed)
        try {
            for (String pair : payload.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "token".equals(URLDecoder.decode(kv[0], StandardCharsets.UTF_8))) {
                    return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {}

        log.warn("LigdiCash webhook : token introuvable dans le payload — {}", payload);
        return null;
    }

    @Override
    public ResultatRemboursement rembourserTransaction(String referenceOperateur, BigDecimal montant) {
        try {
            JsonNode response = client().post()
                    .uri("/redirect/checkout-invoice/refund")
                    .bodyValue(Map.of(
                            "apikey", paymentProperties.getLigdicash().getApiKey(),
                            "token", referenceOperateur,
                            "amount", montant.intValue()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String refRemb = response != null ? response.path("refund_id").asText(null) : null;
            return new ResultatRemboursement(true, refRemb, "Remboursement initié");
        } catch (Exception e) {
            log.error("Échec remboursement LigdiCash pour token={}", referenceOperateur, e);
            return new ResultatRemboursement(false, null, e.getMessage());
        }
    }

    private ConfirmationPaiement echecConfirmation(String token, BigDecimal montant, String motif) {
        return new ConfirmationPaiement(false, null, token, "error", montant, null, null, null, motif);
    }
}
