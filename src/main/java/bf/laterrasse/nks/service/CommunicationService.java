package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Partenaire;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.StatutPartenaire;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.dto.admin.CommunicationRequest;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.gateway.email.EmailGateway;
import bf.laterrasse.nks.gateway.sms.SmsGateway;
import bf.laterrasse.nks.gateway.sms.WhatsappGateway;
import bf.laterrasse.nks.repository.CandidatRepository;
import bf.laterrasse.nks.repository.PartenaireRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/** US-35 — Communication groupée admin → candidats ou partenaires. */
@Service
@RequiredArgsConstructor
public class CommunicationService {

    private final CandidatRepository candidatRepository;
    private final PartenaireRepository partenaireRepository;
    private final NotificationService notificationService;
    private final SmsGateway smsGateway;
    private final EmailGateway emailGateway;
    private final WhatsappGateway whatsappGateway;
    private static final Logger log = LoggerFactory.getLogger(CommunicationService.class);

    public Map<String, Object> envoyerGroupe(CommunicationRequest request) {
        if (request.canalSms() && request.message().length() > 160) {
            throw new ValidationMetierException("Le message SMS est limité à 160 caractères");
        }

        if (request.ciblePartenaires()) {
            return envoyerAuxPartenaires(request);
        }

        List<Candidat> destinataires = request.filtreStatut() == null
                ? candidatRepository.findByEditionIdWithUtilisateur(request.editionId())
                : candidatRepository.findByEditionIdAndStatutWithUtilisateur(request.editionId(), request.filtreStatut());

        int succes = 0;
        for (Candidat candidat : destinataires) {
            Utilisateur utilisateur = candidat.getUtilisateur();
            try {
                if (request.canalSms()) {
                    notificationService.envoyerSms(utilisateur, utilisateur.getTelephone(),
                            TypeNotification.CONVOCATION, request.message());
                }
                if (request.canalEmail()) {
                    notificationService.envoyerEmail(utilisateur, utilisateur.getEmail(),
                            TypeNotification.CONVOCATION,
                            request.sujetEmail() != null ? request.sujetEmail() : "NKS — Information",
                            "<p>" + request.message() + "</p>");
                }
                if (request.canalWhatsapp()) {
                    whatsappGateway.envoyer(utilisateur.getTelephone(), resolveTemplate(request), resolveVariables(request));
                }
                succes++;
            } catch (Exception e) {
                log.error("Échec envoi candidat {} : {}", candidat.getId(), e.getMessage(), e);
            }
        }

        return Map.of("destinataires", destinataires.size(), "envoisDeclenches", succes);
    }

    private Map<String, Object> envoyerAuxPartenaires(CommunicationRequest request) {
        List<Partenaire> partenaires = partenaireRepository.findByStatut(StatutPartenaire.ACTIF);
        int succes = 0;
        for (Partenaire partenaire : partenaires) {
            try {
                String tel = partenaire.getContactTelephone();
                String email = partenaire.getContactEmail();
                if (request.canalSms() && tel != null && !tel.isBlank()) {
                    smsGateway.envoyer(SmsGateway.normaliserTelephone(tel), request.message());
                }
                if (request.canalEmail() && email != null && !email.isBlank()) {
                    emailGateway.envoyer(email,
                            request.sujetEmail() != null ? request.sujetEmail() : "NKS — Information",
                            "<p>" + request.message() + "</p>");
                }
                if (request.canalWhatsapp() && tel != null && !tel.isBlank()) {
                    whatsappGateway.envoyer(SmsGateway.normaliserTelephone(tel), resolveTemplate(request), resolveVariables(request));
                }
                succes++;
            } catch (Exception e) {
                log.error("Échec envoi partenaire {} : {}", partenaire.getId(), e.getMessage(), e);
            }
        }
        return Map.of("destinataires", partenaires.size(), "envoisDeclenches", succes);
    }

    private String resolveTemplate(CommunicationRequest request) {
        return (request.templateWhatsapp() != null && !request.templateWhatsapp().isBlank())
                ? request.templateWhatsapp() : "karaoke_info";
    }

    private List<String> resolveVariables(CommunicationRequest request) {
        String template = resolveTemplate(request);
        return (request.variablesWhatsapp() != null && !request.variablesWhatsapp().isEmpty())
                ? request.variablesWhatsapp()
                : (template.equals("karaoke_info") ? List.of(request.message()) : List.of());
    }
}
