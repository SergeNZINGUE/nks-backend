package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.service.PaiementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §13.7. Toujours répondre 200 rapidement — LigdiCash réessaie sinon (§10.6). */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final PaiementService paiementService;

    @PostMapping("/ligdicash")
    public ResponseEntity<Void> ligdicash(@RequestBody String payload) {
        paiementService.traiterWebhook(payload);
        return ResponseEntity.ok().build();
    }
}
