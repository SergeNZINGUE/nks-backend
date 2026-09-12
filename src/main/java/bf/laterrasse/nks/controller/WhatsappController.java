package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.aop.Auditable;
import bf.laterrasse.nks.dto.sms.SmsRequest;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Point 04 de l'audit — pendant WhatsApp de SmsController. */
@RestController
@RequestMapping("/whatsapp")
@RequiredArgsConstructor
public class WhatsappController {

    private final WhatsappGateway whatsappGateway;

    @PostMapping("/envoyer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Auditable(action = "WHATSAPP_ENVOYE", entite = "Whatsapp")
    public ResponseEntity<?> envoyer(@Valid @RequestBody SmsRequest request) {
        // Envoi unitaire → toujours karaoke_info (message libre)
        String sid = whatsappGateway.envoyer(request.getTo(), "karaoke_info", List.of(request.getMessage()));
        return ResponseEntity.ok(Map.of("success", true, "sid", sid != null ? sid : ""));
    }
}
