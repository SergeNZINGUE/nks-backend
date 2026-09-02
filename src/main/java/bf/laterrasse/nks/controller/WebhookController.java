package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.service.PaiementService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * §13.7. Toujours répondre 200 rapidement — LigdiCash réessaie sinon (§10.6).
 * Restriction IP configurable via LIGDICASH_ALLOWED_IPS (CSV). Si vide : toutes IPs acceptées.
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final PaiementService paiementService;

    @Value("${nks.payment.ligdicash.allowed-ips:}")
    private String allowedIps;

    @PostMapping("/ligdicash")
    public ResponseEntity<Void> ligdicash(@RequestBody String payload, HttpServletRequest request) {
        if (!allowedIps.isBlank()) {
            String remoteIp = request.getRemoteAddr();
            boolean autorisee = Arrays.stream(allowedIps.split(","))
                    .map(String::trim)
                    .anyMatch(remoteIp::equals);
            if (!autorisee) {
                log.warn("Webhook LigdiCash ignoré — IP non autorisée : {}", remoteIp);
                return ResponseEntity.ok().build();
            }
        }
        paiementService.traiterWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
