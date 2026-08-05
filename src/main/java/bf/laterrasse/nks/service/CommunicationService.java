package bf.laterrasse.nks.service;

import bf.laterrasse.nks.domain.Candidat;
import bf.laterrasse.nks.domain.Utilisateur;
import bf.laterrasse.nks.domain.enums.Enums.TypeNotification;
import bf.laterrasse.nks.dto.admin.CommunicationRequest;
import bf.laterrasse.nks.exception.ValidationMetierException;
import bf.laterrasse.nks.repository.CandidatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

/** US-35 — Communication groupée admin → candidats. */
@Service
@RequiredArgsConstructor
public class CommunicationService {

    private final CandidatRepository candidatRepository;
    private final NotificationService notificationService;
    private static final Logger log = LoggerFactory.getLogger(CommunicationService.class);

    public Map<String, Object> envoyerGroupe(CommunicationRequest request) {
        if (request.canalSms() && request.message().length() > 160) {
            throw new ValidationMetierException("Le message SMS est limité à 160 caractères");
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
                succes++;
            } catch (Exception e) {
                log.error("Échec envoi candidat {} : {}", candidat.getId(), e.getMessage(), e);

                // Échec individuel tracé par NotificationService (statut ECHOUE + retry job) — on continue le lot
            }
        }

        return Map.of("destinataires", destinataires.size(), "envoisDeclenches", succes);
    }
}
