package bf.laterrasse.nks.gateway.sms;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Gateway WhatsApp via le proxy HDR Stream.
 * Si NKS_WHATSAPP_URL est vide → mode simulation (aucun appel réseau).
 */
@Component
@Slf4j
public class HdrStreamWhatsappGateway implements WhatsappGateway {

    private final String whatsappUrl;
    private final String apiKey;

    public HdrStreamWhatsappGateway(
            @Value("${nks.sms.whatsapp-url:}") String whatsappUrl,
            @Value("${nks.sms.proxy-api-key:}") String apiKey) {
        this.whatsappUrl = whatsappUrl;
        this.apiKey = apiKey;
    }

    @Override
    public String envoyer(String telephone, String message) {
        if (whatsappUrl == null || whatsappUrl.isBlank()) {
            log.warn("NKS_WHATSAPP_URL non configuré — WhatsApp simulé vers {} : {}", telephone, message);
            return "SIMULATED-WA-" + System.currentTimeMillis();
        }

        try {
            JsonNode response = WebClient.create()
                    .post()
                    .uri(whatsappUrl)
                    .header("Content-Type", "application/json")
                    .header("X-NKS-API-Key", apiKey)
                    .bodyValue(Map.of("to", telephone, "message", message))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                String erreur = response != null ? response.path("error").asText("unknown") : "null response";
                String detail = response != null ? response.path("detail").asText("") : "";
                log.error("HDR Stream WhatsApp échoué vers {} — error={} detail={}", telephone, erreur, detail);
                throw new RuntimeException("Envoi WhatsApp échoué : " + erreur + (detail.isBlank() ? "" : " — " + detail));
            }

            String sid = response.path("sid").asText(null);
            log.info("WhatsApp envoyé vers {} via HDR Stream — sid={}", telephone, sid);
            return sid;

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("HDR Stream WhatsApp HTTP {} pour {} — {}", e.getStatusCode(), telephone, responseBody);
            throw new RuntimeException("Erreur HTTP HDR Stream WhatsApp " + e.getStatusCode() + " : " + responseBody, e);
        }
    }
}
