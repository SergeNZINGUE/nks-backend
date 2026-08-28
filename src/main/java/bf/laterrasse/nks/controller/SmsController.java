package bf.laterrasse.nks.controller;

import bf.laterrasse.nks.dto.sms.SmsBulkResponse;
import bf.laterrasse.nks.dto.sms.SmsRequest;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.repository.CandidatureRepository;
import bf.laterrasse.nks.service.CandidatureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sms")
@RequiredArgsConstructor
@Slf4j
public class SmsController {

    private final SmsGateway smsGateway;
    private final CandidatureRepository candidatureRepository;

    /** Envoi unitaire — admin uniquement. */
    @PostMapping("/envoyer")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<?> envoyer(@Valid @RequestBody SmsRequest request) {
        String sid = smsGateway.envoyer(request.getTo(), request.getMessage());
        return ResponseEntity.ok(Map.of("success", true, "sid", sid != null ? sid : ""));
    }

    /**
     * Envoi en masse du SMS de confirmation aux candidatures EN_ATTENTE_PAIEMENT.
     * Utilise le même message que l'envoi automatique lors de la validation individuelle.
     */
    @PostMapping("/candidatures-validees")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<SmsBulkResponse> envoyerAuxCandidaturesValidees() {
        var candidatures = candidatureRepository.findValideesAvecCandidatEtUtilisateur();
        log.info("Envoi SMS en masse — {} candidature(s) EN_ATTENTE_PAIEMENT", candidatures.size());

        int nbEnvoyes = 0;
        List<SmsBulkResponse.EchecSms> echecs = new ArrayList<>();

        for (var candidature : candidatures) {
            String telephone = candidature.getCandidat().getUtilisateur().getTelephone();
            try {
                smsGateway.envoyer(telephone, CandidatureService.SMS_CANDIDATURE_VALIDEE);
                nbEnvoyes++;
            } catch (Exception e) {
                log.warn("SMS échoué vers {} (candidature {}) : {}", telephone, candidature.getId(), e.getMessage());
                echecs.add(new SmsBulkResponse.EchecSms(telephone, e.getMessage()));
            }
        }

        log.info("Envoi SMS terminé — {} envoyés, {} échecs", nbEnvoyes, echecs.size());
        return ResponseEntity.ok(new SmsBulkResponse(nbEnvoyes, echecs.size(), echecs));
    }
}
