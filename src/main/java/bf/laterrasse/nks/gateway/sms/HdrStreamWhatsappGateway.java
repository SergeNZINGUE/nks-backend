package bf.laterrasse.nks.gateway.sms;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gateway WhatsApp via le proxy HDR Stream (templates Meta pré-approuvés).
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
    public String envoyer(String telephone, String template, List<String> variables) {
        if (whatsappUrl == null || whatsappUrl.isBlank()) {
            log.warn("NKS_WHATSAPP_URL non configuré — WhatsApp simulé vers {} template={} vars={}", telephone, template, variables);
            return "SIMULATED-WA-" + System.currentTimeMillis();
        }

        Map<String, Object> body = new HashMap<>();
        body.put("to", telephone);
        body.put("template", template);
        if (variables != null && !variables.isEmpty()) {
            body.put("variables", variables);
        }

        try {
            JsonNode response = WebClient.create()
                    .post()
                    .uri(whatsappUrl)
                    .header("Content-Type", "application/json")
                    .header("X-NKS-API-Key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.path("success").asBoolean(false)) {
                String erreur = response != null ? response.path("error").asText("unknown") : "null response";
                String detail = response != null ? response.path("detail").asText("") : "";
                log.error("HDR Stream WhatsApp échoué vers {} template={} — error={} detail={}", telephone, template, erreur, detail);
                throw new RuntimeException("Envoi WhatsApp échoué : " + erreur + (detail.isBlank() ? "" : " — " + detail));
            }

            String sid = response.path("sid").asText(null);
            log.info("WhatsApp envoyé vers {} template={} — sid={}", telephone, template, sid);
            return sid;

        } catch (WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("HDR Stream WhatsApp HTTP {} pour {} template={} — {}", e.getStatusCode(), telephone, template, responseBody);
            throw new RuntimeException("Erreur HTTP HDR Stream WhatsApp " + e.getStatusCode() + " : " + responseBody, e);
        }
    }
}
