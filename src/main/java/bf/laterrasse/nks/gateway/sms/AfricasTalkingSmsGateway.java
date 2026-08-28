package bf.laterrasse.nks.gateway.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@ConditionalOnProperty(prefix = "nks.sms", name = "provider", havingValue = "africastalking")
@Slf4j
public class AfricasTalkingSmsGateway implements SmsGateway {

    private final WebClient webClient;
    private final String username;
    private final String apiKey;
    private final String senderId;

    public AfricasTalkingSmsGateway(
            @Value("${nks.sms.africastalking.username:}") String username,
            @Value("${nks.sms.africastalking.api-key:}") String apiKey,
            @Value("${nks.sms.africastalking.sender-id:NKS 2026}") String senderId) {
        this.username = username;
        this.apiKey = apiKey;
        this.senderId = senderId;
        boolean sandbox = "sandbox".equalsIgnoreCase(username);
        String baseUrl = sandbox
                ? "https://api.sandbox.africastalking.com/version1"
                : "https://api.africastalking.com/version1";
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String envoyer(String telephone, String message) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AT_API_KEY non configuré — SMS simulé (Africa's Talking) vers {} : {}", telephone, message);
            return "SIMULATED-" + System.currentTimeMillis();
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("to", telephone);
        form.add("message", message);
        form.add("from", senderId);

        try {
            String response = webClient.post()
                    .uri("/messaging")
                    .header("apiKey", apiKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .bodyValue(form)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            log.info("SMS envoyé vers {} — Africa's Talking : {}", telephone, response);
            return response;
        } catch (Exception e) {
            log.error("Échec envoi SMS (Africa's Talking) vers {} : {}", telephone, e.getMessage());
            throw e;
        }
    }
}
