package bf.laterrasse.nks.gateway.sms;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Gateway SMS via le proxy HDR Stream (expéditeur alphanumérique "NKS 2026").
 * Si NKS_SMS_PROXY_URL est vide → mode simulation (aucun appel réseau).
 */
@Component
@ConditionalOnProperty(prefix = "nks.sms", name = "provider", havingValue = "hdrstream", matchIfMissing = true)
@Slf4j
public class HdrStreamSmsGateway implements SmsGateway {

    private final String proxyUrl;
    private final String apiKey;

    public HdrStreamSmsGateway(
            @Value("${nks.sms.proxy-url:}") String proxyUrl,
            @Value("${nks.sms.proxy-api-key:}") String apiKey) {
        this.proxyUrl = proxyUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String envoyer(String telephone, String message) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            log.warn("NKS_SMS_PROXY_URL non configuré — SMS simulé vers {} : {}", telephone, message);
            return "SIMULATED-" + System.currentTimeMillis();
        }

        try {
            JsonNode response = WebClient.create()
                    .post()
                    .uri(proxyUrl)
                    .header("Content-Type", "application/json")
                    .header("X-NKS-API-Key", apiKey)
                    .bodyValue(Map.of("to", telephone, "message", message))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                String erreur = response != null ? response.path("error").asText("unknown") : "null response";
                String detail = response != null ? response.path("detail").asText("") : "";
                log.error("HDR Stream SMS échoué vers {} — error={} detail={}", telephone, erreur, detail);
                throw new RuntimeException("Envoi SMS échoué : " + erreur + (detail.isBlank() ? "" : " — " + detail));
            }

            String sid = response.path("sid").asText(null);
            log.info("SMS envoyé vers {} via HDR Stream — sid={}", telephone, sid);
            return sid;

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("HDR Stream HTTP {} pour {} — {}", e.getStatusCode(), telephone, responseBody);
            throw new RuntimeException("Erreur HTTP HDR Stream " + e.getStatusCode() + " : " + responseBody, e);
        }
    }
}
